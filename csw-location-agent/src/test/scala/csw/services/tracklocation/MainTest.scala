package csw.services.tracklocation

import java.net.URI
import java.nio.file.Paths

import akka.actor.typed.ActorRef
import com.typesafe.config.ConfigFactory
import csw.common.commons.CoordinatedShutdownReasons.TestFinishedReason
import csw.common.location.Connection.TcpConnection
import csw.common.location.{ComponentId, ComponentType}
import csw.services.location.commons.{ClusterAwareSettings, ClusterSettings}
import csw.services.location.scaladsl.LocationServiceFactory
import csw.services.tracklocation.common.TestFutureExtension.RichFuture
import org.jboss.netty.logging.{InternalLoggerFactory, Slf4JLoggerFactory}
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSuite, Matchers}

import scala.concurrent.duration._

/**
 * Test the csw-location-agent app in-line
 */
class MainTest extends FunSuite with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with Eventually {

  // Fix to avoid 'java.util.concurrent.RejectedExecutionException: Worker has already been shutdown'
  InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory)

  private val clusterSettings: ClusterSettings = ClusterAwareSettings.onPort(3559)
  private val locationService                  = LocationServiceFactory.withSystem(clusterSettings.system)

  implicit val patience: PatienceConfig = PatienceConfig(5.seconds, 100.millis)

  override protected def afterAll(): Unit = locationService.shutdown(TestFinishedReason).await

  test("Test with command line args") {
    val name = "test1"
    val port = 9999
    val args = Array("--name", name, "--command", "sleep 200", "--port", port.toString, "--no-exit")
    testWith(args, name, port)
  }

  test("Test with config file") {
    val name       = "test2"
    val url        = getClass.getResource("/test2.conf")
    val configFile = Paths.get(url.toURI).toFile
    val config     = ConfigFactory.parseFile(configFile)
    val port       = config.getInt("test2.port")

    val args = Array("--name", name, "--no-exit", configFile.getAbsolutePath)
    testWith(args, name, port)
  }

  private def testWith(args: Array[String], name: String, port: Int) = {
    val trackLocationApp = new Main(ClusterAwareSettings.joinLocal(3559))
    val process          = trackLocationApp.start(args).get

    val connection       = TcpConnection(ComponentId(name, ComponentType.Service))
    val resolvedLocation = locationService.resolve(connection, 5.seconds).await.get

    resolvedLocation.connection shouldBe connection
    resolvedLocation.uri shouldBe new URI(s"tcp://${ClusterAwareSettings.hostname}:$port")
    resolvedLocation.logAdminActorRef.isInstanceOf[ActorRef[_]] shouldBe true

    process.destroy()
    eventually(locationService.list.await shouldBe List.empty)
  }
}
