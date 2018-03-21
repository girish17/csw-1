package csw.services.config.client.javadsl;

import akka.actor.ActorSystem;
import akka.stream.Materializer;
import csw.common.commons.CoordinatedShutdownReasons;
import csw.services.config.api.javadsl.IConfigClientService;
import csw.services.config.api.javadsl.IConfigService;
import csw.services.config.api.models.ConfigData;
import csw.services.config.api.models.ConfigId;
import csw.services.config.client.internal.ActorRuntime;
import csw.services.config.server.ServerWiring;
import csw.services.config.server.commons.TestFileUtils;
import csw.services.config.server.http.HttpService;
import csw.services.location.commons.ClusterAwareSettings;
import csw.services.location.javadsl.ILocationService;
import csw.services.location.javadsl.JLocationServiceFactory;
import org.junit.*;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;

// DEOPSCSW-138:Split Config API into Admin API and Client API
// DEOPSCSW-103: Java API for Configuration service
public class JConfigClientApiTest {

    private static ActorRuntime actorRuntime = new ActorRuntime(ActorSystem.create());
    private static ILocationService clientLocationService = JLocationServiceFactory.withSettings(ClusterAwareSettings.onPort(3552));

    private static IConfigService configAdminApi = JConfigClientFactory.adminApi(actorRuntime.actorSystem(), clientLocationService);
    private static IConfigClientService configClientApi = JConfigClientFactory.clientApi(actorRuntime.actorSystem(), clientLocationService);

    private static ServerWiring serverWiring = ServerWiring.make(ClusterAwareSettings.joinLocal(3552, new scala.collection.mutable.ArrayBuffer()));
    private static HttpService httpService = serverWiring.httpService();
    private TestFileUtils testFileUtils = new TestFileUtils(serverWiring.settings());

    private Materializer mat = actorRuntime.mat();

    private String configValue1 = "axisName1 = tromboneAxis\naxisName2 = tromboneAxis2\naxisName3 = tromboneAxis3";
    private String configValue2 = "axisName11 = tromboneAxis\naxisName22 = tromboneAxis2\naxisName3 = tromboneAxis33";
    private String configValue3 = "axisName111 = tromboneAxis\naxisName222 = tromboneAxis2\naxisName3 = tromboneAxis333";


    @BeforeClass
    public static void beforeAll() throws Exception {
        Await.result(httpService.registeredLazyBinding(), Duration.create(20, "seconds"));
    }

    @Before
    public void initSvnRepo() {
        serverWiring.svnRepo().initSvnRepo();
    }

    @After
    public void deleteServerFiles() {
        testFileUtils.deleteServerFiles();
    }

    @AfterClass
    public static void afterAll() throws Exception {
        Await.result(httpService.shutdown(CoordinatedShutdownReasons.testFinishedReason()), Duration.create(20, "seconds"));
        clientLocationService.shutdown(CoordinatedShutdownReasons.testFinishedReason()).get();
        Await.result(actorRuntime.actorSystem().terminate(), Duration.create(20, "seconds"));
    }

    @Test
    public void testConfigClientApi() throws ExecutionException, InterruptedException {
        Path path = Paths.get("/tmt/text-files/trombone_hcd/application.conf");

        configAdminApi.create(path, ConfigData.fromString(configValue1), false, "first commit").get();
        Assert.assertTrue(configClientApi.exists(path).get());

        ConfigId configId1 = configAdminApi.update(path, ConfigData.fromString(configValue2), "second commit").get();
        configAdminApi.update(path, ConfigData.fromString(configValue3), "third commit").get();
        Assert.assertEquals(configClientApi.getActive(path).get().get().toJStringF(mat).get(), configValue1);
        Assert.assertTrue(configClientApi.exists(path, configId1).get());

        configAdminApi.setActiveVersion(path, configId1, "setting active version").get();
        Assert.assertEquals(configClientApi.getActive(path).get().get().toJStringF(mat).get(), configValue2);

        configAdminApi.resetActiveVersion(path, "resetting active version of file").get();
        Assert.assertEquals(configClientApi.getActive(path).get().get().toJStringF(mat).get(), configValue3);
    }
}
