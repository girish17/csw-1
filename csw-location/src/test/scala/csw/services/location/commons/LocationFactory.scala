package csw.services.location.commons

import java.net.URI

import akka.actor.typed.ActorRef
import csw.common.location.Connection.{AkkaConnection, HttpConnection, TcpConnection}
import csw.common.location.{AkkaLocation, HttpLocation, TcpLocation}

object LocationFactory {
  def akka(connection: AkkaConnection, uri: URI, actorRef: ActorRef[_]) =
    AkkaLocation(connection, Some("nfiraos.ncc.trombone"), uri, actorRef, null)
  def http(connection: HttpConnection, uri: URI) = HttpLocation(connection, uri, null)
  def tcp(connection: TcpConnection, uri: URI)   = TcpLocation(connection, uri, null)
}
