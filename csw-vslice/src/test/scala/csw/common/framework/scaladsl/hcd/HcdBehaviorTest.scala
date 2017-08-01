package csw.common.framework.scaladsl.hcd

import akka.typed.testkit.scaladsl.TestProbe
import csw.common.components.hcd.HcdDomainMessage
import csw.common.framework.models.HcdResponseMode
import csw.common.framework.models.HcdResponseMode.{Initialized, Running}
import csw.common.framework.models.InitialHcdMsg.Run
import csw.common.framework.scaladsl.FrameworkComponentTestSuite
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class HcdBehaviorTest extends FrameworkComponentTestSuite with MockitoSugar {

  test("hcd component should send initialize and running message to supervisor") {
    val sampleHcdHandler = mock[HcdHandlers[HcdDomainMessage]]

    when(sampleHcdHandler.initialize()).thenReturn(Future.unit)

    val supervisorProbe: TestProbe[HcdResponseMode] = TestProbe[HcdResponseMode]

    val hcdRef =
      Await.result(
        system.systemActorOf[Nothing](getSampleHcdFactory(sampleHcdHandler).behavior(hcdInfo, supervisorProbe.ref),
                                      "sampleHcd"),
        5.seconds
      )

    val initialized = supervisorProbe.expectMsgType[Initialized]

    verify(sampleHcdHandler).initialize()
    initialized.hcdRef shouldBe hcdRef

    initialized.hcdRef ! Run

    val running = supervisorProbe.expectMsgType[Running]

    verify(sampleHcdHandler).onRun()
    verify(sampleHcdHandler).isOnline_=(true)

    running.hcdRef shouldBe hcdRef
  }
}
