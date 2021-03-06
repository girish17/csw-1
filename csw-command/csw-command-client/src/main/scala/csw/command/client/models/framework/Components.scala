package csw.command.client.models.framework

import csw.serializable.TMTSerializable

/**
 * Represents a collection of components created in a single container
 *
 * @param components a set of components with its supervisor and componentInfo
 */
case class Components(components: Set[Component]) extends TMTSerializable
