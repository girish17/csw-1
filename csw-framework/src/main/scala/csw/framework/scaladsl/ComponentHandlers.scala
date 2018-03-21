package csw.framework.scaladsl

import akka.actor.typed.scaladsl.ActorContext
import csw.common.commands.{CommandResponse, ControlCommand}
import csw.common.framework.ComponentInfo
import csw.common.location.{Connection, TrackingEvent}
import csw.common.scaladsl.TopLevelActorCommonMessage.TrackingEventReceived
import csw.common.scaladsl.TopLevelActorMessage
import csw.services.command.scaladsl.CommandResponseManager
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.LoggerFactory

import scala.concurrent.Future

/**
 * Base class for component handlers which will be used by the component actor
 *
 * @param ctx the ActorContext under which the actor instance of the component, which use these handlers, is created
 * @param componentInfo component related information as described in the configuration file
 * @param currentStatePublisher the pub sub actor to publish state represented by [[csw.common.params.states.CurrentState]] for this component
 * @param locationService the single instance of Location service created for a running application
 */
abstract class ComponentHandlers(
    ctx: ActorContext[TopLevelActorMessage],
    componentInfo: ComponentInfo,
    commandResponseManager: CommandResponseManager,
    currentStatePublisher: CurrentStatePublisher,
    locationService: LocationService,
    loggerFactory: LoggerFactory
) {
  var isOnline: Boolean = false

  /**
   * The initialize handler is invoked when the component is created. This is different than constructor initialization
   * to allow non-blocking asynchronous operations. The component can initialize state such as configuration to be fetched
   * from configuration service, location of components or services to be fetched from location service etc. These vary
   * from component to component.
   *
   * @return a future which completes when the initialization of component completes
   */
  def initialize(): Future[Unit]

  /**
   * The onLocationTrackingEvent handler can be used to take action on the TrackingEvent for a particular connection.
   * This event could be for the connections in ComponentInfo tracked automatically or for the connections tracked
   * explicitly using trackConnection method.
   *
   * @param trackingEvent represents a LocationUpdated or LocationRemoved event received for a tracked connection
   */
  def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit

  /**
   * The validateCommand is invoked when a command is received by this component. If a command can be completed immediately,
   * a CommandResponse indicating the final response for the command can be returned. If a command requires time for processing,
   * the component is required to validate the ControlCommand received and return a validation result as Accepted or Invalid.
   *
   * @param controlCommand represents a command received e.g. Setup, Observe or wait
   * @return a CommandResponse after validation
   */
  def validateCommand(controlCommand: ControlCommand): CommandResponse

  /**
   * On receiving a command as Submit, the onSubmit handler is invoked for a component only if the validateCommand handler
   * returns Accepted. In case a command is received as a submit, command response should be updated in the CommandResponseManager.
   * CommandResponseManager is an actor whose reference commandResponseManager is available in the ComponentHandlers.
   *
   * @param controlCommand represents a command received e.g. Setup, Observe or wait
   */
  def onSubmit(controlCommand: ControlCommand): Unit

  /**
   * On receiving a command as Oneway, the onOneway handler is invoked for a component only if the validateCommand handler
   * returns Accepted.In case a command is received as a oneway, command response should not be provided to the sender.
   *
   * @param controlCommand represents a command received e.g. Setup, Observe or wait
   */
  def onOneway(controlCommand: ControlCommand): Unit

  /**
   * The onShutdown handler can be used for carrying out the tasks which will allow the component to shutdown gracefully
   *
   * @return a future which completes when the shutdown completes for component
   */
  def onShutdown(): Future[Unit]

  /**
   * A component can be notified to run in offline mode in case it is not in use. The component can change its behavior
   * if needed as a part of this handler.
   */
  def onGoOffline(): Unit

  /**
   * A component can be notified to run in online mode again in case it was put to run in offline mode. The component can
   * change its behavior if needed as a part of this handler.
   */
  def onGoOnline(): Unit

  /**
   * Track any connection. The handlers for received events are defined in onLocationTrackingEvent() method
   *
   * @param connection to be tracked for location updates
   */
  def trackConnection(connection: Connection): Unit =
    locationService.subscribe(connection, trackingEvent ⇒ ctx.self ! TrackingEventReceived(trackingEvent))
}
