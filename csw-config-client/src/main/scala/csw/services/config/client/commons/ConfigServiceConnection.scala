package csw.services.config.client.commons

import csw.common.location.Connection.HttpConnection
import csw.common.location.{ComponentId, ComponentType}

/**
 * `ConfigServiceConnection` is a wrapper over predefined `HttpConnection` representing config server. It is used to resolve
 * config service location for client in `ConfigServiceResolver`
 */
private[csw] object ConfigServiceConnection {
  val value = HttpConnection(ComponentId("ConfigServer", ComponentType.Service))
}
