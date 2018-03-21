package csw.common.framework

import akka.actor.ActorSystem

/**
 * SupervisorInfo is used by container while spawning multiple components
 *
 * @param system an ActorSystem used for spawning actors of a component. Each component has it's own actorSystem. The container
 *               keeps all the actorSystems for all components with it and shuts them down when a Shutdown message is
 *               received by a container.
 * @param component represents a supervisor actor reference and componentInfo
 */
case class SupervisorInfo private[common] (system: ActorSystem, component: Component)
