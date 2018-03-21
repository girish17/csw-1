package csw.framework.javadsl

import java.util

import csw.common.framework.{ComponentInfo, LocationServiceUsage}
import csw.common.location.{ComponentType, Connection}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.concurrent.duration.FiniteDuration

/**
 * Helper instance for Java to create [[csw.common.framework.ComponentInfo]]
 */
object JComponentInfo {

  /**
   * The information needed to create a component. This class is created after de-serializing the config file for the component.
   *
   * @param name the name of the component
   * @param componentType the type of the component as defined by [[csw.common.location.ComponentType]]
   * @param prefix identifies the subsystem
   * @param className specifies the component to be created by name of the class of it's factory
   * @param locationServiceUsage specifies component's usage of location service
   * @param connections set of connections that will be used by this component for interaction
   * @param initializeTimeout the timeout value used while initializing a component
   * @return an instance of ComponentInfo
   */
  def from(
      name: String,
      componentType: ComponentType,
      prefix: String,
      className: String,
      locationServiceUsage: LocationServiceUsage,
      connections: util.Set[Connection],
      initializeTimeout: FiniteDuration
  ): ComponentInfo = ComponentInfo(
    name,
    componentType,
    prefix,
    className,
    locationServiceUsage,
    connections.asScala.toSet,
    initializeTimeout
  )
}
