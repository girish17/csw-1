package csw.framework.internal.container

import akka.Done
import akka.actor.testkit.typed.Effect.Watched
import akka.actor.testkit.typed.TestKitSettings
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestProbe}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, SpawnProtocol}
import csw.alarm.client.AlarmServiceFactory
import csw.command.client.messages.ContainerCommonMessage.{GetComponents, GetContainerLifecycleState}
import csw.command.client.messages.ContainerIdleMessage.SupervisorsCreated
import csw.command.client.messages.FromSupervisorMessage.SupervisorLifecycleStateChanged
import csw.command.client.messages.RunningMessage.Lifecycle
import csw.command.client.messages.SupervisorContainerCommonMessages.Restart
import csw.command.client.messages.{ComponentMessage, ContainerActorMessage, ContainerIdleMessage}
import csw.command.client.models.framework.ToComponentLifecycleMessages.{GoOffline, GoOnline}
import csw.command.client.models.framework.{ComponentInfo, ContainerLifecycleState, SupervisorLifecycleState, _}
import csw.event.client.EventServiceFactory
import csw.framework.ComponentInfos._
import csw.framework.FrameworkTestMocks
import csw.framework.internal.supervisor.SupervisorInfoFactory
import csw.framework.scaladsl.RegistrationFactory
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{AkkaRegistration, RegistrationResult}
import csw.location.api.scaladsl.LocationService
import csw.location.client.ActorSystemFactory
import csw.params.core.models.Prefix
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.{Future, Promise}
import scala.util.Success

//DEOPSCSW-182-Control Life Cycle of Components
//DEOPSCSW-216-Locate and connect components to send AKKA commands
class ContainerBehaviorTest extends FunSuite with Matchers with MockitoSugar with ArgumentMatchersSugar {
  implicit val typedSystem: ActorSystem[SpawnProtocol] = ActorSystemFactory.remote(SpawnProtocol.behavior, "test")
  implicit val settings: TestKitSettings               = TestKitSettings(typedSystem)
  private val mocks                                    = new FrameworkTestMocks()

  class IdleContainer() {
    private val testActor: ActorRef[Any]                        = TestProbe("test-probe").ref
    val akkaRegistration                                        = AkkaRegistration(mock[AkkaConnection], Prefix("nfiraos.ncc.trombone"), testActor)
    val locationService: LocationService                        = mock[LocationService]
    val eventService: EventServiceFactory                       = mock[EventServiceFactory]
    val alarmService: AlarmServiceFactory                       = mock[AlarmServiceFactory]
    val registrationResult: RegistrationResult                  = mock[RegistrationResult]
    private[container] var supervisorInfos: Set[SupervisorInfo] = Set.empty
    var componentProbes: Set[TestProbe[ComponentMessage]]       = Set.empty
    val supervisorInfoFactory: SupervisorInfoFactory            = mock[SupervisorInfoFactory]

    private def answer(ci: ComponentInfo): Future[Some[SupervisorInfo]] = {
      val componentProbe: TestProbe[ComponentMessage] = TestProbe(ci.name)
      val supervisorInfo                              = SupervisorInfo(typedSystem, Component(componentProbe.ref, ci))

      supervisorInfos += SupervisorInfo(typedSystem, Component(componentProbe.ref, ci))
      componentProbes += componentProbe
      Future.successful(Some(supervisorInfo))
    }

    when(
      supervisorInfoFactory
        .make(
          any[ActorRef[ContainerIdleMessage]],
          any[ComponentInfo],
          any[LocationService],
          any[EventServiceFactory],
          any[AlarmServiceFactory],
          any[RegistrationFactory]
        )
    ).thenAnswer((_: ActorRef[ContainerIdleMessage], ci: ComponentInfo) => answer(ci))

    private val registrationFactory: RegistrationFactory = mock[RegistrationFactory]
    when(registrationFactory.akkaTyped(any[AkkaConnection], any[Prefix], any[ActorRef[_]]))
      .thenReturn(akkaRegistration)

    private val eventualRegistrationResult: Future[RegistrationResult] =
      Promise[RegistrationResult].complete(Success(registrationResult)).future
    private val eventualDone: Future[Done] = Promise[Done].complete(Success(Done)).future

    when(locationService.register(akkaRegistration)).thenReturn(eventualRegistrationResult)
    when(registrationResult.unregister()).thenReturn(eventualDone)

    val containerBehaviorTestkit: BehaviorTestKit[ContainerActorMessage] = BehaviorTestKit(
      Behaviors.setup(
        ctx ⇒
          new ContainerBehavior(
            ctx,
            containerInfo,
            supervisorInfoFactory,
            registrationFactory,
            locationService,
            eventService,
            alarmService,
            mocks.loggerFactory
          )
      )
    )
  }

  class RunningContainer() extends IdleContainer {
    containerBehaviorTestkit.run(SupervisorsCreated(supervisorInfos))
    val components = Components(supervisorInfos.map(_.component))
    components.components.foreach(
      component ⇒
        containerBehaviorTestkit.run(SupervisorLifecycleStateChanged(component.supervisor, SupervisorLifecycleState.Running))
    )
  }

  test("should change its lifecycle state to running after all components move to running lifecycle state") {
    val idleContainer = new IdleContainer
    import idleContainer._
    verify(locationService).register(akkaRegistration)

    val getComponentsProbe           = TestProbe[Components]
    val containerLifecycleStateProbe = TestProbe[ContainerLifecycleState]

    containerBehaviorTestkit.run(GetComponents(getComponentsProbe.ref))
    getComponentsProbe.expectMessageType[Components]

    // verify that given components in ContainerInfo are created
    containerBehaviorTestkit.selfInbox().receiveMessage() shouldBe a[SupervisorsCreated]

    containerBehaviorTestkit.run(SupervisorsCreated(supervisorInfos))

    containerBehaviorTestkit.run(GetComponents(getComponentsProbe.ref))
    val components = Components(supervisorInfos.map(_.component))
    getComponentsProbe.expectMessage(components)

    // verify that created components are watched by the container
    containerBehaviorTestkit
      .retrieveAllEffects() shouldBe components.components.map(component ⇒ Watched(component.supervisor)).toList

    // simulate that container receives LifecycleStateChanged to Running message from all components
    components.components.foreach(
      component ⇒
        containerBehaviorTestkit.run(SupervisorLifecycleStateChanged(component.supervisor, SupervisorLifecycleState.Running))
    )

    // verify that Container changes its state to Running after all component supervisors change their state to Running
    containerBehaviorTestkit.run(GetContainerLifecycleState(containerLifecycleStateProbe.ref))
    containerLifecycleStateProbe.expectMessage(ContainerLifecycleState.Running)
  }

  test("should handle restart message by changing its lifecycle state to Idle") {
    val runningContainer = new RunningContainer
    import runningContainer._
    val containerLifecycleStateProbe = TestProbe[ContainerLifecycleState]

    // verify that Container is in Running state before sending Restart
    containerBehaviorTestkit.run(GetContainerLifecycleState(containerLifecycleStateProbe.ref))
    containerLifecycleStateProbe.expectMessage(ContainerLifecycleState.Running)

    containerBehaviorTestkit.run(Restart)

    // verify that Container changes its state to Idle after Restart
    containerBehaviorTestkit.run(GetContainerLifecycleState(containerLifecycleStateProbe.ref))
    containerLifecycleStateProbe.expectMessage(ContainerLifecycleState.Idle)

    // verify that Container sends Restart message to all component supervisors
    componentProbes.map(_.expectMessage(Restart))
  }

  test("should change its lifecycle state from Idle to Running after all components have restarted") {
    val runningContainer = new RunningContainer
    import runningContainer._
    val containerLifecycleStateProbe = TestProbe[ContainerLifecycleState]

    containerBehaviorTestkit.run(Restart)

    // verify that Container changes its state to Idle after Restart
    containerBehaviorTestkit.run(GetContainerLifecycleState(containerLifecycleStateProbe.ref))
    containerLifecycleStateProbe.expectMessage(ContainerLifecycleState.Idle)

    // simulate that container receives LifecycleStateChanged to Running message from all components
    components.components.foreach(
      component ⇒
        containerBehaviorTestkit.run(SupervisorLifecycleStateChanged(component.supervisor, SupervisorLifecycleState.Running))
    )

    // verify that Container changes its state to Running after all component supervisors change their state to Running
    containerBehaviorTestkit.run(GetContainerLifecycleState(containerLifecycleStateProbe.ref))
    containerLifecycleStateProbe.expectMessage(ContainerLifecycleState.Running)

  }

  test("should handle GoOnline and GoOffline Lifecycle messages by forwarding to all components") {
    val runningContainer = new RunningContainer
    import runningContainer._
    val containerLifecycleStateProbe = TestProbe[ContainerLifecycleState]

    containerBehaviorTestkit.run(GetContainerLifecycleState(containerLifecycleStateProbe.ref))
    val initialLifecycleState = containerLifecycleStateProbe.expectMessageType[ContainerLifecycleState]

    containerBehaviorTestkit.run(Lifecycle(GoOnline))

    // verify that Container sends GoOnline message to all component supervisors
    componentProbes.map(_.expectMessage(Lifecycle(GoOnline)))

    // verify that Container LifecycleState does not change on receiving GoOnline message
    containerBehaviorTestkit.run(GetContainerLifecycleState(containerLifecycleStateProbe.ref))
    val newLifecycleState = containerLifecycleStateProbe.expectMessage(initialLifecycleState)

    containerBehaviorTestkit.run(Lifecycle(GoOffline))

    // verify that Container sends GoOffline message to all component supervisors
    componentProbes.map(_.expectMessage(Lifecycle(GoOffline)))

    // verify that Container LifecycleState does not change on receiving GoOffline message
    containerBehaviorTestkit.run(GetContainerLifecycleState(containerLifecycleStateProbe.ref))
    containerLifecycleStateProbe.expectMessage(newLifecycleState)
  }
}
