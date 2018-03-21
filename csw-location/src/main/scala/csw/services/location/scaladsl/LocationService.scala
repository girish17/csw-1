package csw.services.location.scaladsl

import acyclic.skipped
import akka.Done
import akka.actor.CoordinatedShutdown.Reason
import akka.stream.KillSwitch
import akka.stream.scaladsl.Source
import csw.common.location._
import csw.services.location.javadsl.ILocationService
import csw.services.location.models._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * A LocationService interface to manage registrations. All operations are non-blocking.
 */
trait LocationService {

  /**
   * Registers a connection -> location in cluster
   *
   * @param registration the Registration holding connection and it's corresponding location to register with `LocationService`
   * @return a future which completes with Registration result or can fail with
   *         [[csw.services.location.exceptions.RegistrationFailed]] or [[csw.services.location.exceptions.OtherLocationIsRegistered]]
   */
  def register(registration: Registration): Future[RegistrationResult]

  /**
   * Unregisters the connection
   *
   * @param connection an already registered connection
   * @return a future which completes after un-registration happens successfully and fails otherwise with
   *         [[csw.services.location.exceptions.UnregistrationFailed]]
   */
  def unregister(connection: Connection): Future[Done]

  /**
   * Unregisters all connections
   *
   * @note it is highly recommended to use this method for testing purpose only
   * @return a future which completes after all connections are unregistered successfully or fails otherwise with
   *         [[csw.services.location.exceptions.RegistrationListingFailed]]
   */
  def unregisterAll(): Future[Done]

  /**
   * Resolves the location for a connection from the local cache
   *
   * @param connection a connection to resolve to with its registered location
   * @return a future which completes with the resolved location if found or None otherwise. It can fail with
   *         [[csw.services.location.exceptions.RegistrationListingFailed]].
   */
  def find[L <: Location](connection: TypedConnection[L]): Future[Option[L]]

  /**
   * Resolves the location for a connection from the local cache, if not found waits for the event to arrive
   * within specified time limit. Returns None if both fail.
   *
   * @param connection a connection to resolve to with its registered location
   * @param within max wait time for event to arrive
   * @tparam L the concrete Location type returned once the connection is resolved
   * @return a future which completes with the resolved location if found or None otherwise. It can fail with
   *         [[csw.services.location.exceptions.RegistrationListingFailed]].
   */
  def resolve[L <: Location](connection: TypedConnection[L], within: FiniteDuration): Future[Option[L]]

  /**
   * Lists all locations registered
   *
   * @return a future which completes with a List of all registered locations or can fail with
   *         [[csw.services.location.exceptions.RegistrationListingFailed]]
   */
  def list: Future[List[Location]]

  /**
   * Filters all locations registered based on a component type
   *
   * @param componentType list components of this `componentType`
   * @return a future which completes with filtered locations or can fail with
   *         [[csw.services.location.exceptions.RegistrationListingFailed]]
   */
  def list(componentType: ComponentType): Future[List[Location]]

  /**
   * Filters all locations registered based on a hostname
   *
   * @param hostname list components running on this `hostname`
   * @return a future which completes with filtered locations or can fail with
   *         [[csw.services.location.exceptions.RegistrationListingFailed]]
   */
  def list(hostname: String): Future[List[Location]]

  /**
   * Filters all locations registered based on a connection type
   *
   * @param connectionType list components of this `connectionType`
   * @return a future which completes with filtered locations or can fail with
   *         [[csw.services.location.exceptions.RegistrationListingFailed]]
   */
  def list(connectionType: ConnectionType): Future[List[Location]]

  /**
   * Filters all locations registered based on a prefix.
   *
   * @note all locations having subsystem prefix that starts with the given prefix
   *       value will be listed.
   * @param prefix list components by this `prefix`
   * @return a future which completes with filtered locations or can fail with
   *         [[csw.services.location.exceptions.RegistrationListingFailed]]
   */
  def listByPrefix(prefix: String): Future[List[AkkaLocation]]

  /**
   * Tracks the connection and send events for modification or removal of its location
   *
   * @param connection the `connection` that is to be tracked
   * @return A stream that emits events related to the connection. It can be cancelled using KillSwitch. This will stop giving
   *         events for earlier tracked connection
   */
  def track(connection: Connection): Source[TrackingEvent, KillSwitch]

  /**
   * Subscribe to tracking events for a connection by providing a callback
   * For each event the callback is invoked.
   * Use this method if you do not want to handle materialization and happy with a side-effecting callback instead
   *
   * @param connection the `connection` that is to be tracked
   * @param callback the callback function of type `TrackingEvent` => Unit which gets executed on receiving any `TrackingEvent`
   * @return a killswitch which can be shutdown to unsubscribe the consumer
   */
  def subscribe(connection: Connection, callback: TrackingEvent ⇒ Unit): KillSwitch

  /**
   * Shuts down the LocationService
   *
   * @see terminate method in [[csw.services.location.commons.CswCluster]]
   * @note it is recommended not to perform any operation on LocationService after calling this method
   * @param reason the reason explaining the shutdown
   * @return a future which completes when the location service has shutdown successfully
   */
  def shutdown(reason: Reason): Future[Done]

  /**
   * Returns the Java API for this instance of location service
   */
  def asJava: ILocationService
}
