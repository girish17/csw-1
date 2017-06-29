package csw.services.logging.scaladsl

import java.net.InetAddress

import akka.actor.ActorSystem
import csw.services.logging.appenders.{LogAppenderBuilder, StdOutAppender}
import csw.services.logging.internal.LoggingSystem

object LoggingSystemFactory extends GenericLogger.Simple {

  //To be used for testing purpose only
  private[logging] def start(): LoggingSystem =
    new LoggingSystem("foo-name", "foo-version", InetAddress.getLocalHost.getHostName, ActorSystem("logging"),
      appenderBuilders = Seq(StdOutAppender))

  def start(name: String,
            version: String,
            hostName: String,
            actorSystem: ActorSystem,
            appenders: Seq[LogAppenderBuilder]): LoggingSystem =
    new LoggingSystem(name, version, hostName, actorSystem, appenders)
}
