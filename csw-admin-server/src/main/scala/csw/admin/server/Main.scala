package csw.admin.server

import akka.Done
import akka.actor.CoordinatedShutdown
import csw.admin.server.cli.{ArgsParser, Options}
import csw.admin.server.wiring.AdminWiring
import csw.location.client.utils.LocationServerStatus

import scala.concurrent.duration.DurationDouble

/**
 * responsible for starting http server which exposes http end point to change/get the log level of components dynamically
 *
 * */
// $COVERAGE-OFF$
object Main extends App {
  private val name = BuildInfo.name

  new ArgsParser(name).parse(args).foreach {
    case Options(maybeAdminPort, locationHost) =>
      LocationServerStatus.requireUp(locationHost)

      val wiring = AdminWiring.make(maybeAdminPort, locationHost)
      import wiring._
      import actorRuntime._
      startLogging(name)

      val logAdminBindingF = adminHttpService.registeredLazyBinding

      coordinatedShutdown.addTask(
        CoordinatedShutdown.PhaseServiceUnbind,
        "unbind-services"
      ) { () ⇒
        val hardDeadline = 30.seconds
        logAdminBindingF.flatMap(_.terminate(hardDeadline)).map(_ ⇒ Done)
      }
  }
}
// $COVERAGE-ON$
