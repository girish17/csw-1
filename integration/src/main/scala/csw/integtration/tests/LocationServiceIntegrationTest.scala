package csw.integtration.tests

import akka.actor.CoordinatedShutdown.UnknownReason
import akka.actor.testkit.typed.TestKitSettings
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{typed, ActorSystem, Props, Scheduler}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import csw.command.extensions.AkkaLocationExt.RichAkkaLocation
import csw.command.messages.CommandMessage.Submit
import csw.integtration.apps.TromboneHCD
import csw.integtration.common.TestFutureExtension.RichFuture
import csw.location.api.commons.ClusterAwareSettings
import csw.location.api.exceptions.OtherLocationIsRegistered
import csw.location.api.models.Connection.{AkkaConnection, HttpConnection}
import csw.location.api.models._
import csw.location.api.scaladsl.LocationService
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.location.http.HTTPLocationService
import csw.params.commands.{CommandName, Setup}
import csw.params.core.models.Prefix

import scala.concurrent.Await
import scala.concurrent.duration.DurationLong

class LocationServiceIntegrationTest extends HTTPLocationService {

  implicit val actorSystem: ActorSystem                = ClusterAwareSettings.system
  implicit private val mat: ActorMaterializer          = ActorMaterializer()
  val locationService: LocationService                 = HttpLocationServiceFactory.makeLocalClient
  implicit val typedSystem: typed.ActorSystem[Nothing] = actorSystem.toTyped
  implicit val sched: Scheduler                        = actorSystem.scheduler
  implicit val timeout: Timeout                        = Timeout(5.seconds)
  implicit val testKitSettings: TestKitSettings        = TestKitSettings(typedSystem)

  override def afterAll(): Unit =
    Await.result(locationService.shutdown(UnknownReason), 5.seconds)

  test("should not allow duplicate akka registration") {
    val tromboneHcdActorRef = actorSystem.actorOf(Props[TromboneHCD], "trombone-hcd")
    val componentId         = ComponentId("trombonehcd", ComponentType.HCD)
    val connection          = AkkaConnection(componentId)

    val registration = AkkaRegistration(connection, Prefix("nfiraos.ncc.trombone"), tromboneHcdActorRef)
    Thread.sleep(4000)
    intercept[OtherLocationIsRegistered] {
      locationService.register(registration).await
    }
  }

  test("should able to resolve and communicate with remote HCD started on another container") {
    val componentId = ComponentId("trombonehcd", ComponentType.HCD)
    val connection  = AkkaConnection(componentId)
    val hcdLocation = locationService.find(connection).await.get

    hcdLocation shouldBe a[AkkaLocation]
    hcdLocation.connection shouldBe connection

    val hcdAkkaLocation = hcdLocation.asInstanceOf[AkkaLocation]

    hcdAkkaLocation.componentRef ! Submit(Setup(Prefix("wfos.prog.cloudcover"), CommandName("Unregister"), None), TestProbe().ref)
    Thread.sleep(3000)

    locationService.list.await should have size 1
  }

  test("list all components") {
    val listOfLocations = locationService.list.await

    listOfLocations should have size 1
  }

  test("should able to resolve remote Service started on another container") {

    val componentId = ComponentId("redisservice", ComponentType.Service)
    val connection  = HttpConnection(componentId)

    val hcdLocation = locationService.find(connection).await.get

    hcdLocation shouldBe a[HttpLocation]
    hcdLocation.connection shouldBe connection
  }
}
