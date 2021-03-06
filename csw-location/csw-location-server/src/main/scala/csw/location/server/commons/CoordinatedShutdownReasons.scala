package csw.location.server.commons

import akka.actor.CoordinatedShutdown

object CoordinatedShutdownReasons {
  case class FailureReason(ex: Throwable) extends CoordinatedShutdown.Reason
}
