package csw.logging.client.javadsl;

import akka.actor.ActorSystem;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.Adapter;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import csw.logging.api.javadsl.ILogger;
import csw.logging.api.models.LoggingLevels;
import csw.logging.client.appenders.LogAppenderBuilder;
import csw.logging.client.commons.LoggingKeys$;
import csw.logging.client.internal.LoggingSystem;
import csw.logging.client.utils.JGenericActor;
import csw.logging.client.utils.JLogUtil;
import csw.logging.client.utils.TestAppender;
import org.junit.*;
import org.scalatestplus.junit.JUnitSuite;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static csw.logging.client.utils.Eventually.eventually;

public class JGenericLoggerTest extends JUnitSuite {
    private static ActorSystem actorSystem = ActorSystem.create("base-system");
    private static LoggingSystem loggingSystem;

    private static List<JsonObject> logBuffer = new ArrayList<>();

    private static JsonObject parse(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, JsonElement.class).getAsJsonObject();
    }

    private static TestAppender testAppender     = new TestAppender(x -> {
        logBuffer.add(parse(x.toString()));
        return null;
    });
    private static List<LogAppenderBuilder> appenderBuilders = List.of(testAppender);


    @BeforeClass
    public static void setup() {
        loggingSystem = JLoggingSystemFactory.start("Logger-Test", "SNAPSHOT-1.0", "localhost", actorSystem, appenderBuilders);
    }

    @After
    public void afterEach() {
        logBuffer.clear();
    }

    @AfterClass
    public static void teardown() throws Exception {
        loggingSystem.javaStop().get();
        Await.result(actorSystem.terminate(), Duration.create(10, TimeUnit.SECONDS));
    }

    private class JGenericLoggerUtil {
        private ILogger logger = JGenericLoggerFactory.getLogger(getClass());

       public void start() {
           JLogUtil.logInBulk(logger);
       }
    }

    // DEOPSCSW-277: Java nested class name is not logged correctly in log messages.
    @Test
    public void testGenericLoggerWithoutComponentName() throws InterruptedException {
        String className = JGenericLoggerTest.JGenericLoggerUtil.class.getName();
        new JGenericLoggerTest.JGenericLoggerUtil().start();

        eventually(java.time.Duration.ofSeconds(10), () -> Assert.assertEquals(6, logBuffer.size()));

        logBuffer.forEach(log -> {
            Assert.assertTrue(log.has(LoggingKeys$.MODULE$.SEVERITY()));
            String severity = log.get(LoggingKeys$.MODULE$.SEVERITY()).getAsString().toLowerCase();

            Assert.assertEquals(JLogUtil.logMsgMap.get(severity), log.get(LoggingKeys$.MODULE$.MESSAGE()).getAsString());
            Assert.assertEquals(className, log.get(LoggingKeys$.MODULE$.CLASS()).getAsString());
            LoggingLevels.Level currentLogLevel = LoggingLevels.Level$.MODULE$.apply(severity);
            Assert.assertTrue(currentLogLevel.$greater$eq(LoggingLevels.TRACE$.MODULE$));
        });
    }

    @Test
    public void testGenericLoggerActorWithoutComponentName() throws InterruptedException {
        ActorRef<String> utilActor = Adapter.spawn(actorSystem, JGenericActor.behavior,"JActorUtil");
        String actorPath = utilActor.path().toString();
        String className = JGenericActor.class.getName();

        JLogUtil.sendLogMsgToActorInBulk(utilActor);

        eventually(java.time.Duration.ofSeconds(10), () -> Assert.assertEquals(6, logBuffer.size()));
        logBuffer.forEach(log -> {
            Assert.assertEquals(actorPath, log.get(LoggingKeys$.MODULE$.ACTOR()).getAsString());

            Assert.assertTrue(log.has(LoggingKeys$.MODULE$.SEVERITY()));
            String severity = log.get(LoggingKeys$.MODULE$.SEVERITY()).getAsString().toLowerCase();

            Assert.assertEquals(severity, log.get(LoggingKeys$.MODULE$.MESSAGE()).getAsString());
            Assert.assertEquals(className, log.get(LoggingKeys$.MODULE$.CLASS()).getAsString());

            LoggingLevels.Level currentLogLevel = LoggingLevels.Level$.MODULE$.apply(severity);
            Assert.assertTrue(currentLogLevel.$greater$eq(LoggingLevels.TRACE$.MODULE$));
        });
    }
}
