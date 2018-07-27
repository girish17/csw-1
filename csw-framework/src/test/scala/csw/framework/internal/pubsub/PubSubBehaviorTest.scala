package csw.framework.internal.pubsub

import akka.actor.testkit.typed.TestKitSettings
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestInbox, TestProbe}
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.actor.typed.scaladsl.{Behaviors, MutableBehavior}
import akka.actor.{typed, ActorSystem}
import csw.framework.FrameworkTestMocks
import csw.messages.framework.PubSub.{Publish, Subscribe, SubscribeOnly, Unsubscribe}
import csw.messages.framework.{LifecycleStateChanged, PubSub, SupervisorLifecycleState}
import csw.messages.params.models.Prefix
import csw.messages.params.states.{CurrentState, StateName}
import csw.messages.{framework, ComponentMessage}
import csw.services.location.commons.ActorSystemFactory
import csw.services.logging.scaladsl.Logger
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class PubSubBehaviorTest extends FunSuite with Matchers with BeforeAndAfterAll {

  trait MutableActorMock[T] { this: MutableBehavior[T] ⇒
    protected lazy val log: Logger = MockitoSugar.mock[Logger]
  }

  implicit val untypedSystem: ActorSystem       = ActorSystemFactory.remote("test-1")
  implicit val system: typed.ActorSystem[_]     = untypedSystem.toTyped
  implicit val testKitSettings: TestKitSettings = TestKitSettings(system)
  private val mocks                             = new FrameworkTestMocks()
  private val prefix                            = Prefix("wfos.red.detector")

  private val lifecycleProbe1    = TestProbe[LifecycleStateChanged]
  private val lifecycleProbe2    = TestProbe[LifecycleStateChanged]
  private val currentStateProbe1 = TestInbox[CurrentState]()
  private val currentStateProbe2 = TestInbox[CurrentState]()
  private val stateName1         = StateName("testStateName1")
  private val stateName2         = StateName("testStateName2")
  private val stateName3         = StateName("testStateName3")
  val currentState1              = CurrentState(prefix, stateName1)
  val currentState2              = CurrentState(prefix, stateName2)
  val currentState3              = CurrentState(prefix, stateName3)

  def createLifecycleStatePubSubBehavior(): BehaviorTestKit[PubSub[LifecycleStateChanged, LifecycleStateChanged]] =
    BehaviorTestKit(Behaviors.setup[PubSub[LifecycleStateChanged, LifecycleStateChanged]](ctx ⇒ new PubSubBehavior(ctx, mocks.loggerFactory)))

  def createCurrentStatePubSubBehavior(): BehaviorTestKit[PubSub[CurrentState, StateName]] =
    BehaviorTestKit(Behaviors.setup[PubSub[CurrentState, StateName]](ctx ⇒ new PubSubBehavior(ctx, mocks.loggerFactory)))

  override protected def afterAll(): Unit = Await.result(untypedSystem.terminate(), 5.seconds)

  test("message should be published to all the subscribers") {
    val pubSubBehavior: BehaviorTestKit[PubSub[LifecycleStateChanged, LifecycleStateChanged]] = createLifecycleStatePubSubBehavior()
    val supervisorProbe                                                = TestProbe[ComponentMessage]

    pubSubBehavior.run(Subscribe(lifecycleProbe1.ref))
    pubSubBehavior.run(Subscribe(lifecycleProbe2.ref))

    pubSubBehavior.run(
      Publish(LifecycleStateChanged(supervisorProbe.ref, SupervisorLifecycleState.Running))
    )
    lifecycleProbe1.expectMessage(framework.LifecycleStateChanged(supervisorProbe.ref, SupervisorLifecycleState.Running))
    lifecycleProbe2.expectMessage(framework.LifecycleStateChanged(supervisorProbe.ref, SupervisorLifecycleState.Running))
  }

  // DEOPSCSW-434 : Allow subscription of CurrentState using StateName
  test("message should be published to the subscribers depending on names") {
    val pubSubBehavior: BehaviorTestKit[PubSub[CurrentState, StateName]] = createCurrentStatePubSubBehavior()

    pubSubBehavior.run(Subscribe(currentStateProbe1.ref))
    pubSubBehavior.run(SubscribeOnly(currentStateProbe2.ref, Set(stateName2, stateName3)))

    pubSubBehavior.run(Publish(currentState1))
    pubSubBehavior.run(Publish(currentState2))
    pubSubBehavior.run(Publish(currentState3))

    val currentStates  = currentStateProbe1.receiveAll()
    val currentStates2 = currentStateProbe2.receiveAll()

    currentStates shouldEqual Seq(currentState1, currentState2, currentState3)
    currentStates2 shouldEqual Seq(currentState2, currentState3)
  }

  test("should not receive messages on un-subscription") {
    val pubSubBehavior: BehaviorTestKit[PubSub[LifecycleStateChanged, LifecycleStateChanged]] = createLifecycleStatePubSubBehavior()
    val supervisorProbe                                                = TestProbe[ComponentMessage]

    pubSubBehavior.run(Subscribe(lifecycleProbe1.ref))
    pubSubBehavior.run(Subscribe(lifecycleProbe2.ref))
    pubSubBehavior.run(Unsubscribe(lifecycleProbe1.ref))

    pubSubBehavior.run(
      Publish(framework.LifecycleStateChanged(supervisorProbe.ref, SupervisorLifecycleState.Running))
    )

    lifecycleProbe2.expectMessage(framework.LifecycleStateChanged(supervisorProbe.ref, SupervisorLifecycleState.Running))
    lifecycleProbe1.expectNoMessage(50.millis)
  }
}
