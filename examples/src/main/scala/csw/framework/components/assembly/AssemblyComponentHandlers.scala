package csw.framework.components.assembly

import java.nio.file.Paths

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import csw.framework.exceptions.{FailureRestart, FailureStop}
import csw.framework.scaladsl.{ComponentHandlers, CurrentStatePublisher}
import csw.common.commands.CommandResponse.Accepted
import csw.common.commands._
import csw.common.framework.ComponentInfo
import csw.common.location._
import csw.common.scaladsl.TopLevelActorMessage
import csw.services.command.scaladsl.{CommandResponseManager, CommandService}
import csw.services.config.api.models.ConfigData
import csw.services.config.api.scaladsl.ConfigClientService
import csw.services.config.client.scaladsl.ConfigClientFactory
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.{Logger, LoggerFactory}

import scala.async.Async._
import scala.concurrent.duration.DurationDouble
import scala.concurrent.{ExecutionContextExecutor, Future}

//#component-handlers-class
class AssemblyComponentHandlers(
    ctx: ActorContext[TopLevelActorMessage],
    componentInfo: ComponentInfo,
    commandResponseManager: CommandResponseManager,
    currentStatePublisher: CurrentStatePublisher,
    locationService: LocationService,
    loggerFactory: LoggerFactory
) extends ComponentHandlers(
      ctx,
      componentInfo,
      commandResponseManager,
      currentStatePublisher,
      locationService,
      loggerFactory: LoggerFactory
    )
//#component-handlers-class
    {

  implicit val ec: ExecutionContextExecutor = ctx.executionContext

  private val log: Logger                                                 = new LoggerFactory(componentInfo.name).getLogger(ctx)
  private val configClient: ConfigClientService                           = ConfigClientFactory.clientApi(ctx.system.toUntyped, locationService)
  private var runningHcds: Map[Connection, Option[CommandService]]        = Map.empty
  private var diagnosticsPublisher: ActorRef[DiagnosticPublisherMessages] = _
  private var commandHandler: ActorRef[CommandHandlerMsgs]                = _

  //#initialize-handler
  override def initialize(): Future[Unit] = async {

    // Initialization could include following steps :

    // 1. fetch config (preferably from configuration service)
    val calculationConfig = await(getAssemblyConfig)

    // 2. create a worker actor which is used by this assembly
    val worker: ActorRef[WorkerActorMsg] = ctx.spawnAnonymous(WorkerActor.make(calculationConfig))

    // 3. find a Hcd connection from the connections provided in componentInfo
    val maybeConnection =
      componentInfo.connections.find(connection ⇒ connection.componentId.componentType == ComponentType.HCD)

    // 4. If an Hcd is found as a connection, resolve its location from location service and create other
    // required worker actors required by this assembly

    maybeConnection match {
      case Some(_) ⇒
        resolveHcd().map {
          case Some(hcd) ⇒
            runningHcds = runningHcds.updated(maybeConnection.get, Some(new CommandService(hcd)(ctx.system)))
            diagnosticsPublisher = ctx.spawnAnonymous(DiagnosticsPublisher.make(runningHcds(maybeConnection.get).get, worker))
            commandHandler = ctx.spawnAnonymous(CommandHandler.make(calculationConfig, runningHcds(maybeConnection.get)))
          case None ⇒ // do something
        }
      case None ⇒ Future.successful(Unit)
    }

  }
  //#initialize-handler

  //#validateCommand-handler
  override def validateCommand(controlCommand: ControlCommand): CommandResponse = controlCommand match {
    case _: Setup   ⇒ Accepted(controlCommand.runId) // validation for setup goes here
    case _: Observe ⇒ Accepted(controlCommand.runId) // validation for observe goes here
  }
  //#validateCommand-handler

  //#onSubmit-handler
  override def onSubmit(controlCommand: ControlCommand): Unit = controlCommand match {
    case setup: Setup     ⇒ submitSetup(setup) // includes logic to handle Submit with Setup config command
    case observe: Observe ⇒ submitObserve(observe) // includes logic to handle Submit with Observe config command
  }
  //#onSubmit-handler

  //#onOneway-handler
  override def onOneway(controlCommand: ControlCommand): Unit = controlCommand match {
    case setup: Setup     ⇒ onewaySetup(setup) // includes logic to handle Oneway with Setup config command
    case observe: Observe ⇒ onewayObserve(observe) // includes logic to handle Oneway with Observe config command
  }
  //#onOneway-handler

  //#onGoOffline-handler
  override def onGoOffline(): Unit = {
    // do something when going offline
  }
  //#onGoOffline-handler

  //#onGoOnline-handler
  override def onGoOnline(): Unit = {
    // do something when going online
  }
  //#onGoOnline-handler

  //#onShutdown-handler
  override def onShutdown(): Future[Unit] = async {
    // clean up resources
  }
  //#onShutdown-handler

  //#onLocationTrackingEvent-handler
  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = trackingEvent match {
    case LocationUpdated(location)   ⇒ // do something for the tracked location when it is updated
    case LocationRemoved(connection) ⇒ // do something for the tracked location when it is no longer available
  }
  //#onLocationTrackingEvent-handler

  private def processSetup(sc: Setup): Unit = {
    sc.commandName.toString match {
      case "forwardToWorker" ⇒
      case x                 ⇒ log.error(s"Invalid command [$x] received.")
    }
  }

  private def processObserve(oc: Observe): Unit = {
    oc.commandName.toString match {
      case "point"   ⇒
      case "acquire" ⇒
      case x         ⇒ log.error(s"Invalid command [$x] received.")
    }
  }

  /**
   * in case of submit command, component writer is required to update commandResponseManager with the result
   */
  private def submitSetup(setup: Setup): Unit = processSetup(setup)

  private def submitObserve(observe: Observe): Unit = processObserve(observe)

  private def onewaySetup(setup: Setup): Unit = processSetup(setup)

  private def onewayObserve(observe: Observe): Unit = processObserve(observe)

  /**
   * Below methods are just here to show how exceptions can be used to either restart or stop supervisor
   * This are snipped in paradox documentation
   * */
  //#failureRestart-Exception
  case class HcdNotFoundException() extends FailureRestart("Could not resolve hcd location. Initialization failure.")

  private def resolveHcd(): Future[Option[AkkaLocation]] = {
    val maybeConnection = componentInfo.connections.find(connection ⇒ connection.componentId.componentType == ComponentType.HCD)
    maybeConnection match {
      case Some(hcd) ⇒
        locationService.resolve(hcd.of[AkkaLocation], 5.seconds).map {
          case Some(akkaLocation) ⇒ Some(akkaLocation)
          case None               ⇒
            // Hcd connection could not be resolved for this Assembly. One option to handle this could be to automatic restart which can give enough time
            // for the Hcd to be available
            throw HcdNotFoundException()
        }
      case _ ⇒ Future.successful(None)
    }
  }
  //#failureRestart-Exception

  // #failureStop-Exception
  case class ConfigNotAvailableException() extends FailureStop("Configuration not available. Initialization failure.")

  private def getAssemblyConfig: Future[ConfigData] = {

    configClient.getActive(Paths.get("tromboneAssemblyContext.conf")).flatMap {
      case Some(config) ⇒ Future.successful(config) // do work
      case None         ⇒
        // required configuration could not be found in the configuration service. Component can choose to stop until the configuration is made available in the
        // configuration service and started again
        throw ConfigNotAvailableException()
    }
  }
  // #failureStop-Exception

}
