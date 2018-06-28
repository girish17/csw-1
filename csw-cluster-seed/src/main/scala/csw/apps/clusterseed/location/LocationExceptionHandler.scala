package csw.apps.clusterseed.location

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import csw.commons.http.{JsonRejectionHandler, JsonSupport}
import csw.services.location.exceptions.{OtherLocationIsRegistered, RegistrationFailed}

class LocationExceptionHandler extends Directives with JsonRejectionHandler {

  def route: Directive[Unit] = handleExceptions(jsonExceptionHandler) & handleRejections(jsonRejectionHandler)

  private val jsonExceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex: RegistrationFailed =>
      complete(JsonSupport.asJsonResponse(StatusCodes.InternalServerError, ex.getMessage))
    case ex: OtherLocationIsRegistered =>
      complete(JsonSupport.asJsonResponse(StatusCodes.BadRequest, ex.getMessage))
    case ex: QueryFilterException =>
      complete(JsonSupport.asJsonResponse(StatusCodes.BadRequest, ex.getMessage))
  }
}
