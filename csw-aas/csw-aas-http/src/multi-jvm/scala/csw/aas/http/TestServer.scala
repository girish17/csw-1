package csw.aas.http

import akka.actor
import akka.actor.typed
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.typed.scaladsl
import csw.aas.http.AuthorizationPolicy.RealmRolePolicy
import csw.location.api.scaladsl.LocationService
import csw.logging.client.scaladsl.LoggingSystemFactory

import scala.concurrent.{ExecutionContext, Future}

class TestServer(locationService: LocationService)(implicit ec: ExecutionContext) {
  val securityDirectives = SecurityDirectives(locationService)
  import securityDirectives._

  val routes: Route = get {
    complete("OK")
  } ~ sPost(RealmRolePolicy("admin")) {
    complete("OK")
  }

  def start(testServerPort: Int): Future[Http.ServerBinding] = {

    implicit val system: typed.ActorSystem[SpawnProtocol] = ActorSystem(SpawnProtocol.behavior, "test")
    implicit val untypedSystem: actor.ActorSystem         = system.toUntyped
    LoggingSystemFactory.start("test-server", "", "", system)
    implicit val materializer: ActorMaterializer = scaladsl.ActorMaterializer()
    Http().bindAndHandle(routes, "0.0.0.0", testServerPort)
  }
}
