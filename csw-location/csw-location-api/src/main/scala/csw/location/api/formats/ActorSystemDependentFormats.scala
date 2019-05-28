package csw.location.api.formats

import java.net.URI

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.serialization.{Serialization, SerializationExtension}
import csw.location.api.models.Connection.{AkkaConnection, HttpConnection, TcpConnection}
import csw.location.api.models.{Location, TrackingEvent}
import csw.params.extensions.Formats
import csw.params.extensions.Formats.MappableFormat
import julienrf.json.derived
import play.api.libs.json.{__, Format, Json, OFormat}

private[csw] trait ActorSystemDependentFormats {
  implicit def actorSystem: ActorSystem[_]

  implicit val akkaConnectionFormat: Format[AkkaConnection] = Json.format[AkkaConnection]
  implicit val tcpConnectionFormat: Format[TcpConnection]   = Json.format[TcpConnection]
  implicit val httpConnectionFormat: Format[HttpConnection] = Json.format[HttpConnection]

  implicit val uriFormat: Format[URI] = Formats.of[String].bimap[URI](_.toString, new URI(_))

  implicit def actorRefFormat[T]: Format[ActorRef[T]] =
    Formats
      .of[String]
      .bimap[ActorRef[T]](
        actorRef => Serialization.serializedActorPath(actorRef.toUntyped),
        path => {
          val provider = SerializationExtension(actorSystem.toUntyped).system.provider
          provider.resolveActorRef(path)
        }
      )

  implicit val locationFormat: OFormat[Location]           = derived.flat.oformat((__ \ "type").format[String])
  implicit val trackingEventFormat: OFormat[TrackingEvent] = derived.flat.oformat((__ \ "type").format[String])
}
