package csw.config.client.scaladsl

import java.nio.file.Paths

import akka.actor.CoordinatedShutdown.UnknownReason
import akka.http.scaladsl.Http
import csw.commons.tagobjects.FileSystemSensitive
import csw.config.api.TokenFactory
import csw.config.api.exceptions.{InvalidInput, NotAllowed, Unauthorized}
import csw.config.api.models.{ConfigData, FileType}
import csw.config.api.scaladsl.ConfigService
import csw.config.client.ConfigClientBaseSuite
import csw.config.server.commons.TestFutureExtension.RichFuture
import csw.config.server.files.Sha1
import csw.config.server.{ConfigServiceTest, ServerWiring}
import csw.location.client.scaladsl.HttpLocationServiceFactory
import org.mockito.Mockito.when

// DEOPSCSW-138: Split Config API into Admin API and Client API
// DEOPSCSW-80: HTTP based access for configuration file
// DEOPSCSW-576: Auth token for Configuration service
class ConfigAdminApiTest extends ConfigServiceTest with ConfigClientBaseSuite {

  override val serverWiring: ServerWiring = ServerWiring.make(securityDirectives)
  import serverWiring.actorRuntime._

  private val httpService           = serverWiring.httpService
  private val clientLocationService = HttpLocationServiceFactory.makeLocalClient

  override val configService: ConfigService = ConfigClientFactory.adminApi(actorSystem, clientLocationService, factory)

  override def beforeAll(): Unit = {
    super[ConfigClientBaseSuite].beforeAll()
    httpService.registeredLazyBinding.await
  }

  override def afterAll(): Unit = {
    Http().shutdownAllConnectionPools().await
    httpService.shutdown(UnknownReason).await
    super[ConfigServiceTest].afterAll()
    super[ConfigClientBaseSuite].afterAll()
  }

  // DEOPSCSW-47: Unique name for configuration file
  // DEOPSCSW-135: Validation of suffix for active and sha files
  test("should throw exception for invalid path") {
    val filePath = Paths.get("/test/sample.$active")

    a[InvalidInput] shouldBe thrownBy(
      configService.create(filePath, ConfigData.fromString(configValue1), annex = false, "invalid path").await
    )
  }

  // DEOPSCSW-27: Storing binary component configurations
  // DEOPSCSW-81: Storing large files in the configuration service
  // DEOPSCSW-131: Detect and handle oversize files
  test("should be able to store and retrieve binary file in annex dir", FileSystemSensitive) {
    val fileName   = "smallBinary.bin"
    val path       = Paths.get(getClass.getClassLoader.getResource(fileName).toURI)
    val configData = ConfigData.fromPath(path)
    val repoPath   = Paths.get(fileName)

    //verify that files smaller than annex-min-file-size go to annex if encoding is Binary
    serverWiring.settings.`annex-min-file-size` should be > configData.length

    val configId =
      configService.create(repoPath, configData, annex = false, s"committing file: $fileName").await

    val expectedContent = configService.getById(repoPath, configId).await.get.toInputStream.toByteArray
    val diskFile        = getClass.getClassLoader.getResourceAsStream(fileName)
    expectedContent shouldBe diskFile.toByteArray

    val list = configService.list(Some(FileType.Annex)).await
    list.map(_.path) shouldBe List(repoPath)

    //Note that configService instance from the server-wiring can be used for assert-only calls for sha files
    //This call is invalid from client side
    val svnConfigData =
      serverWiring.configService
        .getById(Paths.get(s"$fileName${serverWiring.settings.`sha1-suffix`}"), configId)
        .await
        .get
    svnConfigData.toStringF.await shouldBe Sha1.fromConfigData(configData).await
  }

  //DEOPSCSW-75 List the names of configuration files that match a path
  test("should throw invalid input exception if pattern is invalid") {
    a[InvalidInput] shouldBe thrownBy(configService.list(pattern = Some("?i)")).await)
  }

  // DEOPSCSW-576: Auth token for Configuration service
  test("should throw Unauthorized exception when not logged in") {
    val invalidTFactory: TokenFactory = mock[TokenFactory]
    when(invalidTFactory.getToken).thenReturn("")

    val configServiceNotLoggedIn: ConfigService =
      ConfigClientFactory.adminApi(actorSystem, clientLocationService, invalidTFactory)

    val filePath = Paths.get("/test/sample")

    a[Unauthorized.type] shouldBe thrownBy(
      configServiceNotLoggedIn.create(filePath, ConfigData.fromString(configValue1), annex = false, "invalid path").await
    )
  }

  // DEOPSCSW-576: Auth token for Configuration service
  test("should throw NotAllowed exception when user does not have correct role") {
    val invalidTFactory: TokenFactory = mock[TokenFactory]
    when(invalidTFactory.getToken).thenReturn(roleMissingTokenStr)

    val configServiceIncorrectRole: ConfigService =
      ConfigClientFactory.adminApi(actorSystem, clientLocationService, invalidTFactory)

    val filePath = Paths.get("/test/sample")

    a[NotAllowed.type] shouldBe thrownBy(
      configServiceIncorrectRole.create(filePath, ConfigData.fromString(configValue1), annex = false, "invalid path").await
    )
  }
}
