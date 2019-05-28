package csw.logging.client.internal

import java.util.concurrent.ConcurrentHashMap

import com.typesafe.config.Config
import csw.logging.api.models.LoggingLevels.Level
import csw.logging.client.models.ComponentLoggingState

import scala.collection.JavaConverters._
import scala.util.Try

private[logging] object ComponentLoggingStateManager {

  /**
   * Extracts the component-log-levels from logging configuration. It takes the config properties and stores in the map
   * componentName ->
   *
   * @param loggingConfig the logging configuration object
   * @return set of Filters
   */
  def from(loggingConfig: Config): ConcurrentHashMap[String, ComponentLoggingState] = {
    new ConcurrentHashMap[String, ComponentLoggingState](Try {
      loggingConfig
        .getObject("component-log-levels")
        .unwrapped()
        .asScala
        .map {
          case (name, componentLogLevel) ⇒ (name, ComponentLoggingState(Level(componentLogLevel.toString)))
        }
        .toMap
    }.getOrElse(Map.empty).asJava)
  }

  /**
   * Add the component logging state for a component in map componentName -> ComponentLoggingState
   *
   * @param componentName the name of the component
   * @param level the log level for the component
   */
  def add(componentName: String, level: Level): Unit = {
    import csw.logging.client.internal.LoggingState._
    componentsLoggingState.put(componentName, ComponentLoggingState(level))
  }
}
