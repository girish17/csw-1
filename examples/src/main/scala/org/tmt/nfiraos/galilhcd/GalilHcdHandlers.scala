//package org.tmt.nfiraos.galilhcd
//
//import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
//import csw.framework.scaladsl.{ComponentHandlers, CurrentStatePublisher}
//import csw.messages.commands._
//import csw.messages.framework.ComponentInfo
//import csw.messages.location.TrackingEvent
//import csw.messages.params.generics.KeyType
//import csw.messages.params.models.Id
//import csw.messages.scaladsl.TopLevelActorMessage
//import csw.services.command.scaladsl.CommandResponseManager
//import csw.services.location.scaladsl.LocationService
//import csw.services.logging.scaladsl.LoggerFactory
//
//import scala.concurrent.{ExecutionContextExecutor, Future}
//
///**
// * Domain specific logic should be written in below handlers.
// * This handlers gets invoked when component receives messages/commands from other component/entity.
// * For example, if one component sends Submit(Setup(args)) command to GalilHcd,
// * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
// * and if validation is successful, then onSubmit hook gets invoked.
// * You can find more information on this here : https://tmtsoftware.github.io/csw-prod/framework.html
// */
//class GalilHcdHandlers(
//    ctx: ActorContext[TopLevelActorMessage],
//    componentInfo: ComponentInfo,
//    commandResponseManager: CommandResponseManager,
//    currentStatePublisher: CurrentStatePublisher,
//    locationService: LocationService,
//    loggerFactory: LoggerFactory
//) extends ComponentHandlers(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService, loggerFactory) {
//
//  implicit val ec: ExecutionContextExecutor = ctx.executionContext
//  private val log                           = loggerFactory.getLogger
//
//  //#worker-actor
//  sealed trait WorkerCommand
//  case class Sleep(runId: Id, timeInMillis: Long) extends WorkerCommand
//
//  private val workerActor = ctx.spawn(
//    Behaviors.immutable[WorkerCommand]((_, msg) => {
//      msg match {
//        case s: Sleep =>
//          log.trace(s"WorkerActor received sleep command with time of ${s.timeInMillis} ms")
//          // simulate long running command
//          Thread.sleep(s.timeInMillis)
//          commandResponseManager.addOrUpdateCommand(s.runId, CommandResponse.Completed(s.runId))
//        case _ => log.error("Unsupported messsage type")
//      }
//      Behaviors.same
//    }),
//    "WorkerActor"
//  )
//  //#worker-actor
//
//  //#initialize
//  override def initialize(): Future[Unit] = {
//    log.info("In HCD initialize")
//    Future.unit
//  }
//  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
//    log.debug(s"TrackingEvent received: ${trackingEvent.connection.name}")
//  }
//  override def onShutdown(): Future[Unit] = {
//    log.info("HCD is shutting down")
//    Future.unit
//  }
//  //#initialize
//
//  //#validate
//  override def validateCommand(controlCommand: ControlCommand): CommandResponse = {
//    log.info(s"Validating command: ${controlCommand.commandName.name}")
//    controlCommand.commandName.name match {
//      case "sleep" => CommandResponse.Accepted(controlCommand.runId)
//      case x       => CommandResponse.Invalid(controlCommand.runId, CommandIssue.UnsupportedCommandIssue("Command $x not supported."))
//    }
//  }
//  //#validate
//
//  //#onSetup
//  override def onSubmit(controlCommand: ControlCommand): Unit = {
//    log.info(s"Handling command: ${controlCommand.commandName}")
//
//    controlCommand match {
//      case s: Setup   => onSetup(s)
//      case o: Observe => // implement (or not)
//    }
//  }
//
//  def onSetup(setup: Setup): Unit = {
//    val longKey = KeyType.LongKey.make("SleepTime")
//
//    // get param from the Parameter Set in the Setup
//    val longParam = setup(longKey)
//    // values of parameters are arrays.  get the first one (the only one in our case)
//    val sleepTimeInMillis = longParam.values.head
//
//    log.info(s"command payload: ${longParam.keyName} = $sleepTimeInMillis")
//
//    workerActor ! Sleep(setup.runId, sleepTimeInMillis)
//  }
//  //#onSetup
//
//  override def onOneway(controlCommand: ControlCommand): Unit = ???
//
//  override def onGoOffline(): Unit = ???
//
//  override def onGoOnline(): Unit = ???
//
//}