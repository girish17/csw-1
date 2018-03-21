package csw.common.framework

import akka.actor.typed.ActorRef
import csw.common.TMTSerializable
import csw.common.scaladsl.ComponentMessage

/**
 * A class that represents a logical component with it's supervisor actor reference and it's meta information
 *
 * @param supervisor an actorRef supervising this component
 * @param info all information regarding this component
 */
case class Component private[csw] (supervisor: ActorRef[ComponentMessage], info: ComponentInfo) extends TMTSerializable
