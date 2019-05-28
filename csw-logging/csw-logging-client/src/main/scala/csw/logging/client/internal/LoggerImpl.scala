package csw.logging.client.internal

import java.time.Instant

import csw.logging.api.models.LoggingLevels._
import csw.logging.api.models.{AnyId, RequestId}
import csw.logging.api.scaladsl.Logger
import csw.logging.client.commons.{Constants, LoggingKeys}
import csw.logging.client.internal.JsonExtensions.AnyMapToJson
import csw.logging.client.internal.LogActorMessages.{Log, LogAltMessage}
import csw.logging.client.internal.LoggingState._
import csw.logging.client.models.ComponentLoggingState
import csw.logging.macros.{SourceFactory, SourceLocation}

private[csw] class LoggerImpl(maybeComponentName: Option[String], actorName: Option[String]) extends Logger {

  // this is to apply default log level for non-component classes like some common file utility classes
  private[this] val componentName: String = maybeComponentName.getOrElse(Constants.DEFAULT_KEY)

  // default log level will be applied if component specific log level is not provided in logging configuration inside component-log-levels block
  private[this] def componentLoggingState: ComponentLoggingState =
    componentsLoggingState.getOrDefault(componentName, componentsLoggingState.get(Constants.DEFAULT_KEY))

  private def all(
      level: Level,
      id: AnyId,
      msg: ⇒ String,
      map: ⇒ Map[String, Any],
      ex: Throwable,
      sourceLocation: SourceLocation
  ): Unit = {
    val time = Instant.now().toEpochMilli // The current time being written in logs. In future it has to be fetched from time service
    MessageHandler.sendMsg(Log(maybeComponentName, level, id, time, actorName, msg, map.asJsObject, sourceLocation, ex))
  }

  private def has(id: AnyId, level: Level): Boolean =
    id match {
      case id1: RequestId =>
        id1.level match {
          case Some(level1) => level.pos <= level1.pos
          case None         => false
        }
      case noId => false
    }

  // implicit factory makes `file`, `line` and `class` to appear in log statements
  // it uses scala macros to capture these details
  def trace(msg: ⇒ String, map: ⇒ Map[String, Any], ex: Throwable, id: AnyId)(implicit factory: SourceFactory): Unit =
    if (componentLoggingState.doTrace || has(id, TRACE)) all(TRACE, id, msg, map, ex, factory.get())

  def debug(msg: ⇒ String, map: ⇒ Map[String, Any], ex: Throwable, id: AnyId)(implicit factory: SourceFactory): Unit =
    if (componentLoggingState.doDebug || has(id, DEBUG)) all(DEBUG, id, msg, map, ex, factory.get())

  override def info(msg: ⇒ String, map: ⇒ Map[String, Any], ex: Throwable, id: AnyId)(
      implicit factory: SourceFactory
  ): Unit = if (componentLoggingState.doInfo || has(id, INFO)) all(INFO, id, msg, map, ex, factory.get())

  override def warn(msg: ⇒ String, map: ⇒ Map[String, Any], ex: Throwable, id: AnyId)(
      implicit factory: SourceFactory
  ): Unit = if (componentLoggingState.doWarn || has(id, WARN)) all(WARN, id, msg, map, ex, factory.get())

  override def error(msg: ⇒ String, map: ⇒ Map[String, Any], ex: Throwable, id: AnyId)(
      implicit factory: SourceFactory
  ): Unit = if (componentLoggingState.doError || has(id, ERROR)) all(ERROR, id, msg, map, ex, factory.get())

  override def fatal(msg: ⇒ String, map: ⇒ Map[String, Any], ex: Throwable, id: AnyId)(
      implicit factory: SourceFactory
  ): Unit = all(FATAL, id, msg, map, ex, factory.get())

  private[logging] override def alternative(
      category: String,
      m: Map[String, Any],
      ex: Throwable,
      id: AnyId,
      time: Long
  ): Unit = MessageHandler.sendMsg(LogAltMessage(category, time, (m ++ Map(LoggingKeys.CATEGORY -> category)).asJsObject, id, ex))
}
