package csw.framework.internal.supervisor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.testkit.typed.Effect
import akka.testkit.typed.scaladsl.Effects.{Spawned, Watched}
import akka.testkit.typed.scaladsl.{BehaviorTestKit, TestProbe}
import csw.common.components.framework.SampleComponentBehaviorFactory
import csw.framework.ComponentInfos._
import csw.framework.{FrameworkTestMocks, FrameworkTestSuite}
import csw.common.scaladsl.{ComponentMessage, ContainerIdleMessage, SupervisorMessage}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

// DEOPSCSW-163: Provide admin facilities in the framework through Supervisor role
class SupervisorBehaviorTest extends FrameworkTestSuite with MockitoSugar {
  val testMocks: FrameworkTestMocks = frameworkTestMocks()
  import testMocks._

  val containerIdleMessageProbe: TestProbe[ContainerIdleMessage] = TestProbe[ContainerIdleMessage]
  val timerScheduler                                             = mock[TimerScheduler[SupervisorMessage]]

  doNothing()
    .when(timerScheduler)
    .startSingleTimer(
      ArgumentMatchers.eq(SupervisorBehavior.InitializeTimerKey),
      ArgumentMatchers.any[SupervisorMessage],
      ArgumentMatchers.any[FiniteDuration]
    )

  doNothing().when(timerScheduler).cancel(eq(SupervisorBehavior.InitializeTimerKey))

  val supervisorBehavior: Behavior[ComponentMessage] = createBehavior(timerScheduler)
  val componentTLAName                               = s"${hcdInfo.name}-${SupervisorBehavior.ComponentActorNameSuffix}"

  test("Supervisor should create child actors for TLA, pub-sub actor for lifecycle and component state") {
    val supervisorBehaviorTestKit = BehaviorTestKit(supervisorBehavior)

    val effects: immutable.Seq[Effect] = supervisorBehaviorTestKit.retrieveAllEffects()

    val spawnedEffects = effects.map {
      case s: Spawned[_] ⇒ s.childName
      case _             ⇒ ""
    }

    spawnedEffects should contain allOf (
      componentTLAName,
      SupervisorBehavior.PubSubLifecycleActor,
      SupervisorBehavior.PubSubComponentActor
    )
  }

  test("Supervisor should watch child component actor [TLA]") {
    val supervisorBehaviorTestKit = BehaviorTestKit(supervisorBehavior)

    val componentActor       = supervisorBehaviorTestKit.childInbox(componentTLAName).ref
    val pubSubLifecycleActor = supervisorBehaviorTestKit.childInbox(SupervisorBehavior.PubSubLifecycleActor).ref
    val pubSubComponentActor = supervisorBehaviorTestKit.childInbox(SupervisorBehavior.PubSubComponentActor).ref

    supervisorBehaviorTestKit.retrieveAllEffects() should contain(Watched(componentActor))
    supervisorBehaviorTestKit.retrieveAllEffects() should not contain Watched(pubSubLifecycleActor)
    supervisorBehaviorTestKit.retrieveAllEffects() should not contain Watched(pubSubComponentActor)
  }

  private def createBehavior(timerScheduler: TimerScheduler[SupervisorMessage]): Behavior[ComponentMessage] = {

    Behaviors
      .mutable[SupervisorMessage](
        ctx =>
          new SupervisorBehavior(
            ctx,
            timerScheduler,
            None,
            hcdInfo,
            new SampleComponentBehaviorFactory,
            commandResponseManagerFactory,
            registrationFactory,
            locationService,
            loggerFactory
        )
      )
      .narrow
  }
}
