package csw.services.location

import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.TestSink
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import csw.common.location.Connection.{AkkaConnection, HttpConnection, TcpConnection}
import csw.common.location._
import csw.services.location.commons.RegistrationFactory
import csw.services.location.helpers.{LSNodeSpec, TwoMembersAndSeed}
import csw.services.location.scaladsl.RegistrationFactory

import scala.concurrent.duration.DurationInt

class TrackLocationTestMultiJvmNode1 extends TrackLocationTest(0)
class TrackLocationTestMultiJvmNode2 extends TrackLocationTest(0)
class TrackLocationTestMultiJvmNode3 extends TrackLocationTest(0)

class TrackLocationTest(ignore: Int) extends LSNodeSpec(config = new TwoMembersAndSeed) {

  import config._
  import cswCluster.mat

  test("two components should able to track same connection and single component should able to track two components") {
    //create akka connection
    val akkaConnection = AkkaConnection(ComponentId("tromboneHcd", ComponentType.HCD))

    //create http connection
    val httpConnection = HttpConnection(ComponentId("Assembly1", ComponentType.Assembly))

    //create tcp connection
    val tcpConnection = TcpConnection(ComponentId("redis1", ComponentType.Service))

    runOn(seed) {
      val actorRef = cswCluster.actorSystem.spawn(Behavior.empty, "trombone-hcd")
      locationService.register(RegistrationFactory.akka(akkaConnection, actorRef)).await
      enterBarrier("Registration")

      locationService.unregister(akkaConnection).await
      enterBarrier("Akka-unregister")
      enterBarrier("Http-unregister")
      enterBarrier("Tcp-unregister")
    }

    runOn(member1) {
      val port   = 5656
      val prefix = "/trombone/hcd"

      val httpRegistration       = RegistrationFactory.http(httpConnection, port, prefix)
      val httpRegistrationResult = locationService.register(httpRegistration).await

      val (akkaSwitch, akkaProbe) =
        locationService.track(akkaConnection).toMat(TestSink.probe[TrackingEvent])(Keep.both).run()
      val (tcpSwitch, tcpProbe) =
        locationService.track(tcpConnection).toMat(TestSink.probe[TrackingEvent])(Keep.both).run()

      val akkaEvent: TrackingEvent = akkaProbe.requestNext()
      val trackedAkkaConnection    = akkaEvent.asInstanceOf[LocationUpdated].connection
      trackedAkkaConnection shouldBe akkaConnection

      val tcpEvent: TrackingEvent = tcpProbe.requestNext()
      val trackedTcpConnection    = tcpEvent.asInstanceOf[LocationUpdated].connection
      trackedTcpConnection shouldBe tcpConnection

      enterBarrier("Registration")
      enterBarrier("Akka-unregister")

      val akkaRemovedEvent: TrackingEvent = akkaProbe.requestNext()
      val unregisteredAkkaConnection      = akkaRemovedEvent.asInstanceOf[LocationRemoved].connection
      unregisteredAkkaConnection shouldBe akkaConnection

      akkaSwitch.shutdown()
      akkaProbe.request(1)
      akkaProbe.expectComplete()

      tcpSwitch.shutdown()
      tcpProbe.request(1)
      tcpProbe.expectComplete()

      httpRegistrationResult.unregister().await
      enterBarrier("Http-unregister")
      enterBarrier("Tcp-unregister")

      tcpProbe.expectNoMessage(200.millis)

    }

    runOn(member2) {
      val Port                  = 5657
      val tcpRegistration       = RegistrationFactory.tcp(tcpConnection, Port)
      val tcpRegistrationResult = locationService.register(tcpRegistration).await

      val (httpSwitch, httpProbe) =
        locationService.track(httpConnection).toMat(TestSink.probe[TrackingEvent])(Keep.both).run()

      val httpEvent: TrackingEvent = httpProbe.requestNext()
      val trackedHttpConnection    = httpEvent.asInstanceOf[LocationUpdated].connection
      trackedHttpConnection shouldBe httpConnection

      enterBarrier("Registration")
      enterBarrier("Akka-unregister")
      enterBarrier("Http-unregister")

      val httpRemovedEvent: TrackingEvent = httpProbe.requestNext()
      val unregisteredHttpConnection      = httpRemovedEvent.asInstanceOf[LocationRemoved].connection
      unregisteredHttpConnection shouldBe httpConnection

      httpSwitch.shutdown()
      httpProbe.request(1)
      httpProbe.expectComplete()

      tcpRegistrationResult.unregister().await
      enterBarrier("Tcp-unregister")

    }

    enterBarrier("after-2")
  }
}
