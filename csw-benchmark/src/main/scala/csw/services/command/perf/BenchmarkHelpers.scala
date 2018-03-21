package csw.services.command.perf

import akka.actor.{typed, ActorSystem}
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.testkit.typed.scaladsl.TestProbe
import com.typesafe.config.Config
import csw.framework.internal.wiring.{FrameworkWiring, Standalone}
import csw.common.framework.{ContainerLifecycleState, SupervisorLifecycleState}
import csw.common.location.Connection.AkkaConnection
import csw.common.location.{AkkaLocation, ComponentId, ComponentType}
import csw.common.scaladsl.ComponentCommonMessage.GetSupervisorLifecycleState
import csw.common.scaladsl.ContainerCommonMessage.GetContainerLifecycleState
import csw.common.scaladsl.{ComponentMessage, ContainerMessage}
import csw.services.command.scaladsl.CommandService
import csw.services.location.commons.BlockingUtils
import csw.services.location.scaladsl.LocationServiceFactory

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationDouble}

object BenchmarkHelpers {

  def spawnStandaloneComponent(actorSystem: ActorSystem, config: Config): CommandService = {
    val locationService                                  = LocationServiceFactory.withSystem(actorSystem)
    val wiring: FrameworkWiring                          = FrameworkWiring.make(actorSystem, locationService)
    implicit val typedSystem: typed.ActorSystem[Nothing] = actorSystem.toTyped

    val probe = TestProbe[SupervisorLifecycleState]

    Standalone.spawn(config, wiring)
    val akkaLocation: AkkaLocation =
      Await.result(locationService.resolve(AkkaConnection(ComponentId("Perf", ComponentType.HCD)), 5.seconds), 5.seconds).get

    assertThatSupervisorIsRunning(akkaLocation.componentRef, probe, 5.seconds)

    new CommandService(akkaLocation)
  }

  def assertThatContainerIsRunning(
      containerRef: ActorRef[ContainerMessage],
      probe: TestProbe[ContainerLifecycleState],
      duration: Duration
  ): Unit = {
    def getContainerLifecycleState: ContainerLifecycleState = {
      containerRef ! GetContainerLifecycleState(probe.ref)
      probe.expectMessageType[ContainerLifecycleState]
    }

    assert(
      BlockingUtils.poll(getContainerLifecycleState == ContainerLifecycleState.Running, duration),
      s"expected :${ContainerLifecycleState.Running}, found :$getContainerLifecycleState"
    )
  }

  def assertThatSupervisorIsRunning(
      actorRef: ActorRef[ComponentMessage],
      probe: TestProbe[SupervisorLifecycleState],
      duration: Duration
  ): Unit = {
    def getSupervisorLifecycleState: SupervisorLifecycleState = {
      actorRef ! GetSupervisorLifecycleState(probe.ref)
      probe.expectMessageType[SupervisorLifecycleState]
    }

    assert(
      BlockingUtils.poll(getSupervisorLifecycleState == SupervisorLifecycleState.Running, duration),
      s"expected :${SupervisorLifecycleState.Running}, found :$getSupervisorLifecycleState"
    )
  }
}
