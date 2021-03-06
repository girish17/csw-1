package csw.admin.server.log.components

import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.logging.api.scaladsl.Logger
import csw.params.commands.CommandResponse.{Accepted, Completed, SubmitResponse, ValidateCommandResponse}
import csw.params.commands.ControlCommand

import scala.concurrent.Future

case class StartLogging()

class GalilComponentHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {

  val log: Logger = cswCtx.loggerFactory.getLogger

  override def initialize(): Future[Unit] = Future.successful(())

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = ()

  private def startLogging(): Unit = {
    log.trace("Level is trace")
    log.debug("Level is debug")
    log.info("Level is info")
    log.warn("Level is warn")
    log.error("Level is error")
    log.fatal("Level is fatal")
  }

  override def validateCommand(controlCommand: ControlCommand): ValidateCommandResponse = Accepted(controlCommand.runId)

  override def onSubmit(controlCommand: ControlCommand): SubmitResponse = {
    Completed(controlCommand.runId)
  }

  override def onOneway(controlCommand: ControlCommand): Unit =
    if (controlCommand.commandName.name == "StartLogging") startLogging()

  override def onShutdown(): Future[Unit] = Future.successful(())

  override def onGoOffline(): Unit = ()

  override def onGoOnline(): Unit = ()
}
