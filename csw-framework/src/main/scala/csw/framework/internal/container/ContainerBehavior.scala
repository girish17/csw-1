package csw.framework.internal.container

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.CoordinatedShutdown.Reason
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal, Terminated}
import csw.framework.internal.supervisor.SupervisorInfoFactory
import csw.framework.models._
import csw.common.commons.CoordinatedShutdownReasons.{AllActorsWithinContainerTerminatedReason, FailedToCreateSupervisorsReason}
import csw.common.framework._
import csw.common.location.Connection.AkkaConnection
import csw.common.location.{ComponentId, ComponentType}
import csw.common.scaladsl.ContainerCommonMessage.{GetComponents, GetContainerLifecycleState}
import csw.common.scaladsl.ContainerIdleMessage.SupervisorsCreated
import csw.common.scaladsl.FromSupervisorMessage.SupervisorLifecycleStateChanged
import csw.common.scaladsl.RunningMessage.Lifecycle
import csw.common.scaladsl.SupervisorContainerCommonMessages.{Restart, Shutdown}
import csw.common.scaladsl.{ComponentMessage, ContainerActorMessage, ContainerCommonMessage, ContainerIdleMessage}
import csw.services.location.models._
import csw.services.location.scaladsl.{LocationService, RegistrationFactory}
import csw.services.logging.scaladsl.{Logger, LoggerFactory}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * The Behavior of a Container of one or more components, represented as a mutable behavior.
 *
 * @param ctx the ActorContext under which the actor instance of this behavior is created
 * @param containerInfo container related information as described in the configuration file
 * @param supervisorInfoFactory the factory for creating the Supervisors for components described in ContainerInfo
 * @param registrationFactory the factory for creating a typed [[csw.services.location.models.AkkaRegistration]] from
                              [[csw.common.location.Connection.AkkaConnection]]
 * @param locationService the single instance of Location service created for a running application
 */
private[framework] final class ContainerBehavior(
    ctx: ActorContext[ContainerActorMessage],
    containerInfo: ContainerInfo,
    supervisorInfoFactory: SupervisorInfoFactory,
    registrationFactory: RegistrationFactory,
    locationService: LocationService,
    loggerFactory: LoggerFactory
) extends Behaviors.MutableBehavior[ContainerActorMessage] {

  import ctx.executionContext
  private val log: Logger                        = loggerFactory.getLogger(ctx)
  private val akkaConnection                     = AkkaConnection(ComponentId(containerInfo.name, ComponentType.Container))
  private val akkaRegistration: AkkaRegistration = registrationFactory.akkaTyped(akkaConnection, ctx.self)

  // Set of successfully created supervisors for components
  var supervisors: Set[SupervisorInfo] = Set.empty

  // Set of created supervisors which moved into Running state
  var runningComponents: Set[ActorRef[ComponentMessage]] = Set.empty
  var lifecycleState: ContainerLifecycleState            = ContainerLifecycleState.Idle

  registerWithLocationService()

  // Failure in registration above doesn't affect creation of components
  createComponents(containerInfo.components)

  /**
   * Defines processing for a [[csw.common.scaladsl.ContainerActorMessage]] received by the actor instance.
   *
   * @param msg containerMessage received
   * @return the existing behavior
   */
  override def onMessage(msg: ContainerActorMessage): Behavior[ContainerActorMessage] = {
    log.debug(s"Container in lifecycle state :[$lifecycleState] received message :[$msg]")
    (lifecycleState, msg) match {
      case (_, msg: ContainerCommonMessage)                          ⇒ onCommon(msg)
      case (ContainerLifecycleState.Idle, msg: ContainerIdleMessage) ⇒ onIdle(msg)
      case (ContainerLifecycleState.Running, msg: Lifecycle)         ⇒ supervisors.foreach(_.component.supervisor ! msg)
      case (_, message) ⇒
        log.error(s"Unexpected message :[$message] received by container in lifecycle state :[$lifecycleState]")
    }
    this
  }

  /**
   * Defines processing for a [[akka.actor.typed.Signal]] received by the actor instance
   *
   * @return the existing behavior
   */
  override def onSignal: PartialFunction[Signal, Behavior[ContainerActorMessage]] = {
    case Terminated(supervisor) ⇒
      log.warn(
        s"Container in lifecycle state :[$lifecycleState] received terminated signal from supervisor :[$supervisor]"
      )
      supervisors = supervisors.filterNot(_.component.supervisor == supervisor)
      if (supervisors.isEmpty) {
        log.warn("All supervisors from this container are terminated. Initiating co-ordinated shutdown.")
        coordinatedShutdown(AllActorsWithinContainerTerminatedReason)
      }
      this
    case PostStop ⇒
      log.warn(s"Un-registering container from location service")
      locationService.unregister(akkaConnection)
      this
  }

  /**
   * Defines action for messages which can be received in any [[csw.common.framework.ContainerLifecycleState]] state
   *
   * @param commonMessage message representing a message received in any lifecycle state
   */
  private def onCommon(commonMessage: ContainerCommonMessage): Unit = commonMessage match {
    case GetComponents(replyTo) ⇒
      replyTo ! Components(supervisors.map(_.component))
    case GetContainerLifecycleState(replyTo) ⇒
      replyTo ! lifecycleState
    case Restart ⇒
      log.debug(s"Container is changing lifecycle state from [$lifecycleState] to [${ContainerLifecycleState.Idle}]")
      lifecycleState = ContainerLifecycleState.Idle
      runningComponents = Set.empty
      supervisors.foreach(_.component.supervisor ! Restart)
    case Shutdown ⇒
      log.debug(s"Container is changing lifecycle state from [$lifecycleState] to [${ContainerLifecycleState.Idle}]")
      lifecycleState = ContainerLifecycleState.Idle
      supervisors.foreach(_.component.supervisor ! Shutdown)
  }

  /**
   * Defines action for messages which can be received in [[csw.common.framework.ContainerLifecycleState.Idle]] state
   *
   * @param idleMessage message representing a message received in [[csw.common.framework.ContainerLifecycleState.Idle]] state
   */
  private def onIdle(idleMessage: ContainerIdleMessage): Unit = idleMessage match {
    case SupervisorsCreated(supervisorInfos) ⇒
      if (supervisorInfos.isEmpty) {
        log.error(s"Failed to spawn supervisors for ComponentInfo's :[${containerInfo.components.mkString(", ")}]")
        coordinatedShutdown(FailedToCreateSupervisorsReason)
      } else {
        supervisors = supervisorInfos
        log.info(s"Container created following supervisors :[${supervisors.map(_.component.supervisor).mkString(",")}]")
        supervisors.foreach(supervisorInfo ⇒ ctx.watch(supervisorInfo.component.supervisor))
        updateContainerStateToRunning()
      }
    case SupervisorLifecycleStateChanged(supervisor, supervisorLifecycleState) ⇒
      if (supervisorLifecycleState == SupervisorLifecycleState.Running) {
        runningComponents = runningComponents + supervisor
        updateContainerStateToRunning()
      }
  }

  /**
   * Create supervisors for all components and return a set of all successfully created supervisors as a message to self
   *
   * @param componentInfos components to be created as specified in the configuration file
   */
  private def createComponents(componentInfos: Set[ComponentInfo]): Unit = {
    log.info(s"Container is creating following components :[${componentInfos.map(_.name).mkString(", ")}]")
    Future
      .traverse(componentInfos) { ci ⇒
        supervisorInfoFactory.make(ctx.self, ci, locationService, registrationFactory)
      }
      .foreach(x ⇒ {
        ctx.self ! SupervisorsCreated(x.flatten)
      })
  }

  /**
   * Updates ContainerLifecycleState to running if all successfully created supervisors move into running state
   */
  private def updateContainerStateToRunning(): Unit = {
    if (runningComponents.size == supervisors.size) {
      log.debug(s"Container is changing lifecycle state from [$lifecycleState] to [${ContainerLifecycleState.Running}]")
      lifecycleState = ContainerLifecycleState.Running
    }
  }

  private def registerWithLocationService(): Unit = {
    log.debug(
      s"Container with connection :[${akkaRegistration.connection.name}] is registering with location service with ref :[${akkaRegistration.actorRef}]"
    )
    locationService.register(akkaRegistration).onComplete {
      case Success(_)         ⇒ log.info(s"Container Registration successful with connection: [$akkaConnection]")
      case Failure(throwable) ⇒ log.error(throwable.getMessage, ex = throwable)
    }
  }

  private def coordinatedShutdown(reason: Reason): Future[Done] = CoordinatedShutdown(ctx.system.toUntyped).run(reason)
}
