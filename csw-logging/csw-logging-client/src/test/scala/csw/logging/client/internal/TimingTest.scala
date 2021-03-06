package csw.logging.client.internal

import java.nio.file.Paths
import java.time.{ZoneId, ZoneOffset, ZonedDateTime}

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import csw.logging.api.models.RequestId
import csw.logging.client.appenders.FileAppender
import csw.logging.client.commons.AkkaTypedExtension.UserActorFactory
import csw.logging.client.commons.LoggingKeys
import csw.logging.client.components.IRIS
import csw.logging.client.components.IRIS._
import csw.logging.client.internal.JsonExtensions.RichJsObject
import csw.logging.client.utils.{FileUtils, LoggingTestSuite}
import play.api.libs.json.{JsObject, Json}

import scala.collection.mutable

// DEOPSCSW-649: Fixed directory configuration for multi JVM scenario
class TimingTest extends LoggingTestSuite with Timing {
  private val logFileDir        = Paths.get("/tmp/csw-test-logs/").toFile
  private val loggingSystemName = "TimingTest"

  override lazy val actorSystem = ActorSystem(SpawnProtocol.behavior, "timing-test-system")
  override lazy val loggingSystem =
    new LoggingSystem(loggingSystemName, "version", "localhost", actorSystem)

  private val irisActorRef = actorSystem.spawn(IRIS.behavior(IRIS.COMPONENT_NAME), name = "IRIS-Supervisor-Actor")

  private val fileTimestamp   = FileAppender.decideTimestampForFile(ZonedDateTime.now(ZoneId.from(ZoneOffset.UTC)))
  private val timeLogFilePath = logFileDir + s"/${loggingSystemName}_${fileTimestamp}_time.log"
  private val testLogFilePath = logFileDir + s"/${loggingSystemName}_$fileTimestamp.log"

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    FileUtils.deleteRecursively(logFileDir)
    loggingSystem.setAppenders(List(testAppender, FileAppender))
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    FileUtils.deleteRecursively(logFileDir)
  }

  def sendLogMsgToIRISActor(): Unit = {
    irisActorRef ! LogTrace
    irisActorRef ! LogDebug
    irisActorRef ! LogInfo
    irisActorRef ! LogWarn
    irisActorRef ! LogError
    irisActorRef ! LogFatal
  }

  private val timerTestRegionName        = "Timer-Test"
  private val startEndTimeTestRegionName = "Start-End-Time-Test"
  private val timerRegionQueue           = mutable.Queue[String](timerTestRegionName, startEndTimeTestRegionName)

  def logMessagesWithTimer(): Unit =
    Time(RequestId(), timerTestRegionName) {
      sendLogMsgToIRISActor()
    }

  def logMessagesWithStartAndEndTimer(): Unit = {
    val id         = RequestId()
    val startToken = time.start(id, startEndTimeTestRegionName)
    sendLogMsgToIRISActor()
    time.end(id, startEndTimeTestRegionName, startToken)
  }

  // DEOPSCSW-142: Flexibility of logging approaches
  // DEOPSCSW-122: Allow local component logs to be output to STDOUT
  // DEOPSCSW-123: Allow local component logs to be output to a file
  test("should able to log messages to combination of standard out and file concurrently and also log time messages.") {

    logMessagesWithTimer()
    logMessagesWithStartAndEndTimer()
    Thread.sleep(300)

    // Reading time logger file
    val timeLogBuffer = FileUtils.read(timeLogFilePath)
    // Reading common logger file
    val fileLogBuffer = FileUtils.read(testLogFilePath)

    // validating timer logger
    timeLogBuffer.toList.foreach { log ⇒
      val itemsMap = log("items").as[List[String]].map(x ⇒ Json.parse(x).as[JsObject]).head

      itemsMap.getString("name") shouldBe timerRegionQueue.dequeue
      itemsMap.contains("time0") shouldBe true
      itemsMap.contains("time1") shouldBe true
      itemsMap.contains("total") shouldBe true
      log.getString(LoggingKeys.NAME) shouldBe "TimingTest"
    }

    // validating file logger
    testLogBuffer(fileLogBuffer)
    // validating console logger
    testLogBuffer(logBuffer)

    def testLogBuffer(logBuffer: mutable.Buffer[JsObject]): Unit = {
      logBuffer.foreach { log ⇒
        val currentLogLevel = log.getString(LoggingKeys.SEVERITY).toLowerCase
        log.getString(LoggingKeys.MESSAGE) shouldBe IRIS.irisLogs(currentLogLevel)

        log.getString(LoggingKeys.COMPONENT_NAME) shouldBe IRIS.COMPONENT_NAME
        log.getString(LoggingKeys.ACTOR) shouldBe irisActorRef.path.toString
        log.getString(LoggingKeys.FILE) shouldBe IRIS.FILE_NAME
        log.getString(LoggingKeys.CLASS) shouldBe IRIS.CLASS_NAME
        log.contains(LoggingKeys.LINE) shouldBe true
      }
    }
  }

}
