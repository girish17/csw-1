package csw.config.server.http

import java.net.BindException

import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown.UnknownReason
import akka.stream.ActorMaterializer
import csw.aas.core.commons.AASConnection
import csw.config.server.ServerWiring
import csw.config.server.commons.ConfigServiceConnection
import csw.config.server.commons.TestFutureExtension.RichFuture
import csw.location.api.exceptions.OtherLocationIsRegistered
import csw.location.api.models.HttpRegistration
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.location.server.commons.NetworksUtil
import csw.location.server.http.HTTPLocationService
import csw.network.utils.Networks

import scala.util.control.NonFatal

class HttpServiceTest extends HTTPLocationService {

  implicit val system: ActorSystem    = ActorSystem("test")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  private val testLocationService     = HttpLocationServiceFactory.makeLocalClient

  //register AAS with location service
  private val AASPort = 8080
  testLocationService.register(HttpRegistration(AASConnection.value, AASPort, "auth"))

  override def afterAll(): Unit = {
    system.terminate().await
    super.afterAll()
  }

  test("should start the http server and register with location service") {
    val _servicePort = 4005
    val serverWiring = ServerWiring.make(Some(_servicePort))
    import serverWiring._
    val (binding, registrationResult) = httpService.registeredLazyBinding.await
    locationService.find(ConfigServiceConnection.value).await.get.connection shouldBe ConfigServiceConnection.value

    val location = registrationResult.location
    location.uri.getHost shouldBe NetworksUtil().hostname
    location.connection shouldBe ConfigServiceConnection.value
    actorRuntime.shutdown(UnknownReason).await
  }

  test("should not register with location service if server binding fails") {
    val _servicePort = 3553 // Location Service runs on this port
    val serverWiring = ServerWiring.make(Some(_servicePort))

    import serverWiring._

    a[BindException] shouldBe thrownBy(httpService.registeredLazyBinding.await)
    testLocationService.find(ConfigServiceConnection.value).await shouldBe None
  }

  test("should not start server if registration with location service fails") {
    val _servicePort = 4007
    val serverWiring = ServerWiring.make(Some(_servicePort))
    import serverWiring._
    locationService.register(HttpRegistration(ConfigServiceConnection.value, 21212, "")).await

    locationService.find(ConfigServiceConnection.value).await.get.connection shouldBe ConfigServiceConnection.value

    a[OtherLocationIsRegistered] shouldBe thrownBy(httpService.registeredLazyBinding.await)

    //TODO: Find a way to assert server is not bounded
    try actorRuntime.shutdown(UnknownReason).await
    catch { case NonFatal(ex) ⇒ }
  }
}
