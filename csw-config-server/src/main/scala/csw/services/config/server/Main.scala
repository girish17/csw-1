package csw.services.config.server

import csw.common.commons.CoordinatedShutdownReasons.FailureReason
import csw.services.config.server.cli.{ArgsParser, Options}
import csw.services.config.server.commons.ConfigServerLogger
import csw.services.config.server.http.HttpService
import csw.services.location.commons.{ClusterAwareSettings, ClusterSettings}
import csw.services.logging.scaladsl.Logger
import org.tmatesoft.svn.core.SVNException

import scala.concurrent.Await
import scala.concurrent.duration.DurationDouble

/**
 * Application object to start the ConfigServer from command line.
 */
class Main(clusterSettings: ClusterSettings, startLogging: Boolean = false) {
  private val log: Logger = ConfigServerLogger.getLogger

  def start(args: Array[String]): Option[HttpService] =
    new ArgsParser().parse(args).map {
      case Options(init, maybePort) =>
        val wiring = ServerWiring.make(clusterSettings, maybePort)
        import wiring._

        if (startLogging) actorRuntime.startLogging()
        if (init) svnRepo.initSvnRepo()

        try {
          svnRepo.testConnection()                                    // first test if the svn repo can be accessed successfully
          Await.result(httpService.registeredLazyBinding, 15.seconds) // then start the config server and register it with location service
          httpService
        } catch {
          case ex: SVNException ⇒
            Await.result(actorRuntime.shutdown(FailureReason(ex)), 10.seconds) // actorRuntime.shutdown will gracefully quit the self node from cluster
            val runtimeException =
              new RuntimeException(s"Could not open repository located at : ${settings.svnUrl}", ex)
            log.error(runtimeException.getMessage, ex = runtimeException)
            throw runtimeException
        }
    }
}

// $COVERAGE-OFF$
object Main extends App {
  if (ClusterAwareSettings.seedNodes.isEmpty) {
    println(
      "clusterSeeds setting is not specified either as env variable or system property. Please check online documentation for this set-up."
    )
  } else {
    new Main(ClusterAwareSettings, startLogging = true).start(args)
  }
}
// $COVERAGE-ON$
