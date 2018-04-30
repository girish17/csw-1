package csw.services.event.perf

import java.io.PrintStream
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.{ExecutorService, Executors}

import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec, MultiNodeSpecCallbacks}
import akka.testkit._
import com.typesafe.config.ConfigFactory
import csw.services.event.perf.EventUtils.{nanosToMicros, nanosToSeconds}
import io.lettuce.core.RedisClient
import org.HdrHistogram.Histogram
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._

object EventServicePerfTest extends MultiNodeConfig {
  val first: RoleName  = role("first")
  val second: RoleName = role("second")

  val barrierTimeout: FiniteDuration = 5.minutes

  commonConfig(debugConfig(on = false).withFallback(ConfigFactory.load()))
}

class EventServicePerfTestMultiJvmNode1 extends EventServicePerfTest
class EventServicePerfTestMultiJvmNode2 extends EventServicePerfTest

class EventServicePerfTest
    extends MultiNodeSpec(EventServicePerfTest)
    with MultiNodeSpecCallbacks
    with FunSuiteLike
    with Matchers
    with ImplicitSender
    with PerfFlamesSupport
    with BeforeAndAfterAll {

  import EventServicePerfTest._

  val testConfigs = new TestConfigs(system.settings.config)
  import testConfigs._
  private val mayBeRedisClient = if (redisEnabled) Some(RedisClient.create()) else None

  var throughputPlots: PlotResult = PlotResult()
  var latencyPlots: LatencyPlots  = LatencyPlots()

  def adjustedTotalMessages(n: Long): Long = (n * totalMessagesFactor).toLong

  override def initialParticipants: Int = roles.size

  lazy val reporterExecutor: ExecutorService = Executors.newFixedThreadPool(1)
  def reporter(name: String): TestRateReporter = {
    val r = new TestRateReporter(name)
    reporterExecutor.execute(r)
    r
  }

  override def beforeAll(): Unit = multiNodeSpecBeforeAll()

  override def afterAll(): Unit = {
    reporterExecutor.shutdown()
    runOn(second) {
      println("================================ Throughput msgs/s ================================")
      throughputPlots.printTable()
      println()
      latencyPlots.printTable(system.name)
    }
    multiNodeSpecAfterAll()
  }

  def testScenario(testSettings: TestSettings, benchmarkFileReporter: BenchmarkFileReporter): Unit = {
    import testSettings._
    val subscriberName           = testName + "-subscriber"
    var aggregatedEventsReceived = 0L
    var totalTime                = 0L

    runPerfFlames(first, second)(delay = 5.seconds, time = 40.seconds)

    runOn(second) {
      val rep                            = reporter(testName)
      val aggregatedHistogram: Histogram = new Histogram(SECONDS.toNanos(10), 3)

      val subscribers = for (n ← 1 to publisherSubscriberPairs) yield {
        val publisherId = if (testSettings.singlePublisher) 1 else n
        val subscriber  = new Subscriber(testSettings, testConfigs, rep, publisherId, n, mayBeRedisClient)
        (subscriber.startSubscription(), subscriber)
      }

      enterBarrier(subscriberName + "-started")

      subscribers.foreach {
        case (doneF, subscriber) ⇒
          Await.result(doneF, 5.minute)
          subscriber.printResult()

          aggregatedHistogram.add(subscriber.histogram)
          aggregatedEventsReceived += subscriber.eventsReceived
          totalTime = Math.max(totalTime, subscriber.totalTime)
      }

      aggregateResult(testSettings, aggregatedEventsReceived, totalTime, aggregatedHistogram)
      enterBarrier(testName + "-done")

      rep.halt()
    }

    runOn(first) {
      val noOfPublishers = if (testSettings.singlePublisher) 1 else publisherSubscriberPairs
      println(
        "================================================================================================================================================"
      )
      println(
        s"[$testName]: Starting benchmark with $noOfPublishers publishers & $publisherSubscriberPairs subscribers $totalTestMsgs messages with " +
        s"throttling of $elements msgs/${per.toSeconds}s " +
        s"and payload size $payloadSize bytes"
      )
      println(
        "================================================================================================================================================"
      )

      enterBarrier(subscriberName + "-started")

      for (n ← 1 to noOfPublishers) yield {
        new Publisher(testSettings, testConfigs, n, mayBeRedisClient).startPublishing()
      }

      enterBarrier(testName + "-done")
    }
    enterBarrier("after-" + testName)
  }

  private def aggregateResult(
      testSettings: TestSettings,
      aggregatedEventsReceived: Long,
      totalTime: Long,
      aggregatedHistogram: Histogram
  ): Unit = {
    import testSettings._
    val throughput = aggregatedEventsReceived / nanosToSeconds(totalTime)

    throughputPlots = throughputPlots.addAll(PlotResult().add(testName, throughput))

    def percentile(p: Double): Double = nanosToMicros(aggregatedHistogram.getValueAtPercentile(p))

    val latencyPlotsTmp = LatencyPlots(
      PlotResult().add(testName, percentile(50.0)),
      PlotResult().add(testName, percentile(90.0)),
      PlotResult().add(testName, percentile(99.0))
    )

    latencyPlots = latencyPlots.copy(
      plot50 = latencyPlots.plot50.addAll(latencyPlotsTmp.plot50),
      plot90 = latencyPlots.plot90.addAll(latencyPlotsTmp.plot90),
      plot99 = latencyPlots.plot99.addAll(latencyPlotsTmp.plot99)
    )

    aggregatedHistogram.outputPercentileDistribution(
      new PrintStream(BenchmarkFileReporter.apply(s"Aggregated-$testName", system, logSettings = false).fos),
      1000.0
    )
  }

  private val reporter  = BenchmarkFileReporter("PerfSpec", system)
  private val scenarios = new Scenarios(testConfigs)

  for (s ← scenarios.warmUp :: scenarios.normal) {
    test(s"Perf results must be great for ${s.testName} with payloadSize = ${s.payloadSize}") {
      testScenario(s, reporter)
    }
  }

}
