package csw.location.server

import akka.Done
import akka.actor.CoordinatedShutdown
import csw.location.server.cli.{ArgsParser, Options}
import csw.location.server.commons.ClusterAwareSettings
import csw.location.server.internal.ServerWiring
import csw.network.utils.Networks
import csw.services.BuildInfo

import scala.concurrent.duration.DurationDouble

/**
 * responsible for starting following:
 *  1. location service on provided port (this is required to bootstrap akka cluster, initially cluster will have single seed node)
 *  2. http server which exposes http end point to change/get the log level of components dynamically
 *
 * */
// $COVERAGE-OFF$
object Main extends App {
  private val name = BuildInfo.name

  new ArgsParser(name).parse(args).foreach {
    case Options(maybeClusterPort, testMode) =>
      if (!testMode && ClusterAwareSettings.seedNodes.isEmpty) {
        println(
          "[ERROR] clusterSeeds setting is not specified either as env variable or system property. Please check online documentation for this set-up."
        )
      } else {
        val wiring =
          if (testMode) ServerWiring.make(Networks.defaultInterfaceName, maybeClusterPort)
          else ServerWiring.make(maybeClusterPort)

        import wiring._
        import actorRuntime._
        startLogging(name, wiring.clusterSettings.hostname)

        val locationBindingF = locationHttpService.start()

        coordinatedShutdown.addTask(
          CoordinatedShutdown.PhaseServiceUnbind,
          "unbind-services"
        ) { () ⇒
          locationBindingF.flatMap(_.terminate(30.seconds)).map(_ ⇒ Done)
        }
      }
  }
}
