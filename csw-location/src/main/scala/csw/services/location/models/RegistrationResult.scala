package csw.services.location.models

import akka.Done
import csw.common.location.Location

import scala.concurrent.Future

/**
 * RegistrationResult represents successful registration of a location
 */
trait RegistrationResult {

  /**
   * The successful registration of location can be unregistered using this method
   *
   * @return a future which completes when un-registrstion is done successfully or fails with
   *         [[csw.services.location.exceptions.UnregistrationFailed]]
   */
  def unregister(): Future[Done]

  /**
   * The `unregister` method will use the connection of this location to unregister from `LocationService`
   *
   * @return the handle to the `Location` that got registered in `LocationService`
   */
  def location: Location
}
