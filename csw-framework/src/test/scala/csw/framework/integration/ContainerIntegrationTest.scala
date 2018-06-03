package csw.framework.integration

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.typed.TestKitSettings
import akka.testkit.typed.scaladsl.TestProbe
import akka.{actor, testkit}
import com.typesafe.config.ConfigFactory
import csw.common.FrameworkAssertions._
import csw.common.components.framework.SampleComponentState._
import csw.framework.internal.wiring.{Container, FrameworkWiring}
import csw.messages.framework
import csw.messages.framework.PubSub.Subscribe
import csw.messages.framework.ToComponentLifecycleMessages.{GoOffline, GoOnline}
import csw.messages.framework.{Components, ContainerLifecycleState, LifecycleStateChanged, SupervisorLifecycleState}
import csw.messages.location.ComponentType.{Assembly, HCD}
import csw.messages.location.Connection.AkkaConnection
import csw.messages.location.{ComponentId, ComponentType, LocationRemoved, TrackingEvent}
import csw.messages.params.states.{CurrentState, StateName}
import csw.messages.scaladsl.ComponentCommonMessage.{GetSupervisorLifecycleState, LifecycleStateSubscription}
import csw.messages.scaladsl.ContainerCommonMessage.{GetComponents, GetContainerLifecycleState}
import csw.messages.scaladsl.RunningMessage.Lifecycle
import csw.messages.scaladsl.SupervisorContainerCommonMessages.{Restart, Shutdown}
import csw.services.command.scaladsl.CommandService
import csw.services.location.commons.ClusterSettings
import csw.services.location.scaladsl.{LocationService, LocationServiceFactory}
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.DurationLong

// DEOPSCSW-169: Creation of Multiple Components
// DEOPSCSW-177: Hooks for lifecycle management
// DEOPSCSW-182: Control Life Cycle of Components
// DEOPSCSW-216: Locate and connect components to send AKKA commands
class ContainerIntegrationTest extends FunSuite with Matchers with BeforeAndAfterAll {

  implicit val seedActorSystem: actor.ActorSystem     = ClusterSettings().onPort(3555).system
  private val containerActorSystem: actor.ActorSystem = ClusterSettings().joinLocal(3555).system

  implicit val typedSystem: ActorSystem[_]      = seedActorSystem.toTyped
  implicit val testKitSettings: TestKitSettings = TestKitSettings(typedSystem)

  implicit val mat: Materializer               = ActorMaterializer()
  private val locationService: LocationService = LocationServiceFactory.withSystem(seedActorSystem)

  private val irisContainerConnection  = AkkaConnection(ComponentId("IRIS_Container", ComponentType.Container))
  private val filterAssemblyConnection = AkkaConnection(ComponentId("Filter", Assembly))
  private val instrumentHcdConnection  = AkkaConnection(ComponentId("Instrument_Filter", HCD))
  private val disperserHcdConnection   = AkkaConnection(ComponentId("Disperser", HCD))

  override protected def afterAll(): Unit = Await.result(seedActorSystem.terminate(), 5.seconds)

  test("should start multiple components withing a single container and able to accept lifecycle messages") {

    val wiring = FrameworkWiring.make(containerActorSystem)
    // start a container and verify it moves to running lifecycle state
    val containerRef =
      Await.result(Container.spawn(ConfigFactory.load("container.conf"), wiring), 5.seconds)

    val componentsProbe              = TestProbe[Components]("comp-probe")
    val containerLifecycleStateProbe = TestProbe[ContainerLifecycleState]("container-lifecycle-state-probe")
    val assemblyProbe                = TestProbe[CurrentState]("assembly-state-probe")
    val filterProbe                  = TestProbe[CurrentState]("filter-state-probe")
    val disperserProbe               = TestProbe[CurrentState]("disperser-state-probe")

    val assemblyLifecycleStateProbe  = TestProbe[LifecycleStateChanged]("assembly-lifecycle-probe")
    val filterLifecycleStateProbe    = TestProbe[LifecycleStateChanged]("filter-lifecycle-probe")
    val disperserLifecycleStateProbe = TestProbe[LifecycleStateChanged]("disperser-lifecycle-probe")

    // initially container is put in Idle lifecycle state and wait for all the components to move into Running lifecycle state
    // ********** Message: GetContainerLifecycleState **********
    containerRef ! GetContainerLifecycleState(containerLifecycleStateProbe.ref)
    containerLifecycleStateProbe.expectMessage(ContainerLifecycleState.Idle)

    assertThatContainerIsRunning(containerRef, containerLifecycleStateProbe, 5.seconds)

    // resolve container using location service
    val containerLocation = Await.result(locationService.resolve(irisContainerConnection, 5.seconds), 5.seconds)

    containerLocation.isDefined shouldBe true
    val resolvedContainerRef = containerLocation.get.containerRef

    // ********** Message: GetComponents **********
    resolvedContainerRef ! GetComponents(componentsProbe.ref)
    val components = componentsProbe.expectMessageType[Components].components
    components.size shouldBe 3

    // resolve all the components from container using location service
    val filterAssemblyLocation = Await.result(locationService.find(filterAssemblyConnection), 5.seconds)
    val instrumentHcdLocation  = Await.result(locationService.find(instrumentHcdConnection), 5.seconds)
    val disperserHcdLocation   = Await.result(locationService.find(disperserHcdConnection), 5.seconds)

    filterAssemblyLocation.isDefined shouldBe true
    instrumentHcdLocation.isDefined shouldBe true
    disperserHcdLocation.isDefined shouldBe true

    val assemblySupervisor  = filterAssemblyLocation.get.componentRef
    val filterSupervisor    = instrumentHcdLocation.get.componentRef
    val disperserSupervisor = disperserHcdLocation.get.componentRef

    val assemblyCommandService  = new CommandService(filterAssemblyLocation.get)
    val filterCommandService    = new CommandService(instrumentHcdLocation.get)
    val disperserCommandService = new CommandService(disperserHcdLocation.get)

    // DEOPSCSW-372: Provide an API for PubSubActor that hides actor based interaction
    // Subscribe to component's current state
    assemblyCommandService.subscribeCurrentState(assemblyProbe.ref ! _)
    filterCommandService.subscribeCurrentState(filterProbe.ref ! _)
    disperserCommandService.subscribeCurrentState(disperserProbe.ref ! _)

    // Subscribe to component's lifecycle state
    assemblySupervisor ! LifecycleStateSubscription(Subscribe(assemblyLifecycleStateProbe.ref))
    filterSupervisor ! LifecycleStateSubscription(Subscribe(filterLifecycleStateProbe.ref))
    disperserSupervisor ! LifecycleStateSubscription(Subscribe(disperserLifecycleStateProbe.ref))

    val supervisorLifecycleStateProbe = TestProbe[SupervisorLifecycleState]
    assemblySupervisor ! GetSupervisorLifecycleState(supervisorLifecycleStateProbe.ref)
    filterSupervisor ! GetSupervisorLifecycleState(supervisorLifecycleStateProbe.ref)
    disperserSupervisor ! GetSupervisorLifecycleState(supervisorLifecycleStateProbe.ref)

    // make sure that all the components are in running lifecycle state before sending lifecycle messages
    supervisorLifecycleStateProbe.expectMessage(SupervisorLifecycleState.Running)
    supervisorLifecycleStateProbe.expectMessage(SupervisorLifecycleState.Running)
    supervisorLifecycleStateProbe.expectMessage(SupervisorLifecycleState.Running)

    // lifecycle messages gets forwarded to all components and their corresponding handlers gets invoked
    // ********** Message: Lifecycle(GoOffline) **********
    resolvedContainerRef ! Lifecycle(GoOffline)
    assemblyProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(offlineChoice))))
    filterProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(offlineChoice))))
    disperserProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(offlineChoice))))

    // Message: Lifecycle(GoOnline)
    resolvedContainerRef ! Lifecycle(GoOnline)
    assemblyProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(onlineChoice))))
    filterProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(onlineChoice))))
    disperserProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(onlineChoice))))

    // on Restart message, container falls back to Idle lifecycle state and wait for all the components to get restarted
    // and moves to Running lifecycle state
    // component handlers hooks should be invoked in following sequence:
    // 1. onShutdown (old TLA)
    // 2. initialize (new TLA)
    // 3. onRun (new TLA)
    // ********** Message: Restart **********
    resolvedContainerRef ! Restart

    resolvedContainerRef ! GetContainerLifecycleState(containerLifecycleStateProbe.ref)
    containerLifecycleStateProbe.expectMessage(ContainerLifecycleState.Idle)

    assemblyProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(shutdownChoice))))
    filterProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(shutdownChoice))))
    disperserProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(shutdownChoice))))

    assemblyProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(initChoice))))
    filterProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(initChoice))))
    disperserProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(initChoice))))

    assemblyLifecycleStateProbe.expectMessage(
      LifecycleStateChanged(assemblySupervisor, SupervisorLifecycleState.Running)
    )
    filterLifecycleStateProbe.expectMessage(
      framework.LifecycleStateChanged(filterSupervisor, SupervisorLifecycleState.Running)
    )
    disperserLifecycleStateProbe.expectMessage(
      framework.LifecycleStateChanged(disperserSupervisor, SupervisorLifecycleState.Running)
    )

    assertThatContainerIsRunning(resolvedContainerRef, containerLifecycleStateProbe, 2.seconds)

    val containerTracker      = testkit.TestProbe()
    val filterAssemblyTracker = testkit.TestProbe()
    val instrumentHcdTracker  = testkit.TestProbe()
    val disperserHcdTracker   = testkit.TestProbe()

    // start tracking container and all the components, so that on Shutdown message, all the trackers gets LocationRemoved event
    locationService
      .track(irisContainerConnection)
      .toMat(Sink.actorRef[TrackingEvent](containerTracker.ref, "Completed"))(Keep.both)
      .run()

    locationService
      .track(filterAssemblyConnection)
      .toMat(Sink.actorRef[TrackingEvent](filterAssemblyTracker.ref, "Completed"))(Keep.both)
      .run()

    locationService
      .track(instrumentHcdConnection)
      .toMat(Sink.actorRef[TrackingEvent](instrumentHcdTracker.ref, "Completed"))(Keep.both)
      .run()

    locationService
      .track(disperserHcdConnection)
      .toMat(Sink.actorRef[TrackingEvent](disperserHcdTracker.ref, "Completed"))(Keep.both)
      .run()

    // ********** Message: Shutdown **********

    resolvedContainerRef ! Shutdown

    // this proves that ComponentBehaviors postStop signal gets invoked for all components
    // as onShutdownHook of all TLA gets invoked from postStop signal
    assemblyProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(shutdownChoice))))
    filterProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(shutdownChoice))))
    disperserProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(shutdownChoice))))

    // this proves that postStop signal of all supervisor's gets invoked
    // as supervisor gets unregistered in postStop signal
    val filterAssemblyRemoved = filterAssemblyTracker.fishForSpecificMessage(5.seconds) { case x: LocationRemoved ⇒ x }
    val instrumentHcdRemoved  = instrumentHcdTracker.fishForSpecificMessage(5.seconds) { case x: LocationRemoved  ⇒ x }
    val disperserHcdRemoved   = disperserHcdTracker.fishForSpecificMessage(5.seconds) { case x: LocationRemoved   ⇒ x }
    val containerRemoved      = containerTracker.fishForSpecificMessage(5.seconds) { case x: LocationRemoved      ⇒ x }

    filterAssemblyRemoved.connection shouldBe filterAssemblyConnection
    instrumentHcdRemoved.connection shouldBe instrumentHcdConnection
    disperserHcdRemoved.connection shouldBe disperserHcdConnection
    containerRemoved.connection shouldBe irisContainerConnection

    // this proves that on shutdown message, container's actor system gets terminated
    // if it does not get terminated in 5 seconds, future will fail which in turn fail this test
    Await.result(containerActorSystem.whenTerminated, 5.seconds)
  }
}
