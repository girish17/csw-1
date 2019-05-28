package csw.location.client.scaladsl

import akka.actor.typed.ActorSystem
import akka.stream.Materializer
import csw.location.api.scaladsl.LocationService
import csw.location.client.internal.{LocationServiceClient, Settings}

/**
 * The factory is used to create LocationService instance.
 */
object HttpLocationServiceFactory {

  private val httpServerPort = Settings().serverPort

  /**
   * Use this factory method to create http location client when location server is running locally.
   * HTTP Location server runs on port 7654.
   * */
  def makeLocalClient(implicit actorSystem: ActorSystem[_], mat: Materializer): LocationService = make("localhost")

  /**
   * Use this factory method to create http location client when location server ip is known.
   * HTTP Location server runs on port 7654.
   * */
  private[csw] def make(serverIp: String)(implicit actorSystem: ActorSystem[_], mat: Materializer): LocationService =
    new LocationServiceClient(serverIp, httpServerPort)

}
