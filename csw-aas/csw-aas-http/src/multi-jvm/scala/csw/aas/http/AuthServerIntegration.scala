package csw.aas.http

import csw.aas.core.commons.AASConnection
import csw.aas.core.deployment.AuthServiceLocation
import csw.location.api.models.Connection.HttpConnection
import csw.location.api.models.{ComponentId, ComponentType, HttpRegistration}
import csw.location.helpers.LSNodeSpec
import csw.location.server.http.MultiNodeHTTPLocationService
import org.scalatest.BeforeAndAfterEach
import org.tmt.embedded_keycloak.KeycloakData.{ApplicationUser, Client, Realm}
import org.tmt.embedded_keycloak.utils.{BearerToken, Ports}
import org.tmt.embedded_keycloak.{EmbeddedKeycloak, KeycloakData, Settings}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class AuthIntegrationTestMultiJvmNode1 extends AuthIntegrationTest
class AuthIntegrationTestMultiJvmNode2 extends AuthIntegrationTest
class AuthIntegrationTestMultiJvmNode3 extends AuthIntegrationTest

//DEOPSCSW-579: Prevent unauthorized access based on akka http route rules
class AuthIntegrationTest
    extends LSNodeSpec(config = new MultiNodeTestConfig, mode = "http")
    with BeforeAndAfterEach
    with MultiNodeHTTPLocationService {

  import config._

  val testServerPort = 3001
  val keycloakPort   = 8081

  private val defaultTimeout: FiniteDuration = 10.seconds
  private val serverTimeout: FiniteDuration  = 30.minutes

  test("it should return 401 for unauthenticated request") {

    runOn(keycloak) {
      val embeddedKeycloak = new EmbeddedKeycloak(KeycloakData.empty)
      val stopHandle       = Await.result(embeddedKeycloak.startServer(), serverTimeout)
      new AuthServiceLocation(locationService).register(Settings.default.port).await
      enterBarrier("keycloak started")
      enterBarrier("test-server started")
      enterBarrier("test finished")
      stopHandle.stop()
    }

    runOn(exampleServer) {
      enterBarrier("keycloak started")
      locationService.resolve(AASConnection.value, defaultTimeout).await

      val stopHandle   = new TestServer(locationService).start(testServerPort).await
      val registration = HttpRegistration(HttpConnection(ComponentId("TestServer", ComponentType.Service)), testServerPort, "")
      locationService.register(registration).await

      enterBarrier("test-server started")
      enterBarrier("test finished")
      stopHandle.unbind()
    }

    runOn(testClient) {
      enterBarrier("keycloak started")
      enterBarrier("test-server started")

      val testServer =
        locationService.resolve(HttpConnection(ComponentId("TestServer", ComponentType.Service)), defaultTimeout).await.get

      requests
        .post(testServer.uri.toString)
        .statusCode shouldBe 401
      enterBarrier("test finished")
    }

    enterBarrier("end")
  }

  test("it should return 200 for authenticated request - valid authorization") {
    runOn(keycloak) {
      val embeddedKeycloak = new EmbeddedKeycloak(
        KeycloakData(
          realms = Set(
            Realm(
              "example",
              clients = Set(
                Client("my-app", "public", passwordGrantEnabled = true, authorizationEnabled = true),
                Client("my-server", "bearer-only", passwordGrantEnabled = false, authorizationEnabled = true)
              ),
              users = Set(ApplicationUser("john", "secret", realmRoles = Set("admin"))),
              realmRoles = Set("admin")
            )
          )
        )
      )
      val stopHandle = Await.result(embeddedKeycloak.startServer(), serverTimeout)
      new AuthServiceLocation(locationService).register(Settings.default.port).await
      enterBarrier("keycloak started")
      enterBarrier("test-server started")
      enterBarrier("test finished")
      stopHandle.stop()
    }

    runOn(exampleServer) {
      enterBarrier("keycloak started")
      locationService.resolve(AASConnection.value, defaultTimeout).await

      val stopHandle   = new TestServer(locationService).start(testServerPort).await
      val registration = HttpRegistration(HttpConnection(ComponentId("TestServer", ComponentType.Service)), testServerPort, "")
      locationService.register(registration).await

      enterBarrier("test-server started")
      enterBarrier("test finished")
      stopHandle.unbind()
    }

    runOn(testClient) {
      enterBarrier("keycloak started")
      enterBarrier("test-server started")

      val token = BearerToken.fromServer(
        keycloakPort,
        "john",
        "secret",
        "example",
        "my-app",
        host = testConductor.getAddressFor(keycloak).await.host.get
      )

      val testServer =
        locationService.resolve(HttpConnection(ComponentId("TestServer", ComponentType.Service)), defaultTimeout).await.get
      requests
        .post(url = testServer.uri.toString, auth = token)
        .statusCode shouldBe 200
      enterBarrier("test finished")
    }

    enterBarrier("end")
  }

  test("it should return 403 for unauthorized request") {
    runOn(keycloak) {
      val embeddedKeycloak = new EmbeddedKeycloak(
        KeycloakData(
          realms = Set(
            Realm(
              "example",
              clients = Set(
                Client("my-app", "public", passwordGrantEnabled = true, authorizationEnabled = true)
              ),
              users = Set(ApplicationUser("john", "secret")),
              realmRoles = Set("admin")
            )
          )
        )
      )
      val stopHandle = Await.result(embeddedKeycloak.startServer(), serverTimeout)
      new AuthServiceLocation(locationService).register(Settings.default.port).await
      enterBarrier("keycloak started")
      enterBarrier("test-server started")
      enterBarrier("test finished")
      stopHandle.stop()
    }

    runOn(exampleServer) {
      enterBarrier("keycloak started")
      locationService.resolve(AASConnection.value, defaultTimeout).await

      val stopHandle   = new TestServer(locationService).start(testServerPort).await
      val registration = HttpRegistration(HttpConnection(ComponentId("TestServer", ComponentType.Service)), testServerPort, "")
      locationService.register(registration).await

      enterBarrier("test-server started")
      enterBarrier("test finished")
      stopHandle.unbind()
    }

    runOn(testClient) {
      enterBarrier("keycloak started")
      enterBarrier("test-server started")

      val token = BearerToken.fromServer(
        keycloakPort,
        "john",
        "secret",
        "example",
        "my-app",
        host = testConductor.getAddressFor(keycloak).await.host.get
      )

      val testServer =
        locationService.resolve(HttpConnection(ComponentId("TestServer", ComponentType.Service)), defaultTimeout).await.get
      requests
        .post(url = testServer.uri.toString, auth = token)
        .statusCode shouldBe 403
      enterBarrier("test finished")
    }

    enterBarrier("end")
  }

  override def beforeEach(): Unit = {
    Ports.stop(keycloakPort)
    super.beforeEach()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    Ports.stop(keycloakPort)
  }
}
