package csw.services.logging.javadsl

import java.net.InetAddress

import akka.actor.ActorSystem
import csw.services.logging.appenders.{LogAppenderBuilder, StdOutAppender}
import csw.services.logging.internal.LoggingSystem

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

object JLoggingSystemFactory {

  //To be used only for testing only
  private[logging] def start(): LoggingSystem = {
    new LoggingSystem("foo-name", "foo-version", InetAddress.getLocalHost.getHostName, ActorSystem("logging"),
      Seq(StdOutAppender))
  }

  def start(name: String,
            version: String,
            hostName: String,
            actorSystem: ActorSystem,
            appenders: java.util.List[LogAppenderBuilder]): LoggingSystem = {
    new LoggingSystem(name, version, hostName, actorSystem, appenders.asScala.toSeq)
  }
}
