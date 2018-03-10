package csw.messages.framework

import akka.typed.ActorRef
import csw.messages.TMTSerializable
import csw.messages.scaladsl.ComponentMessage

/**
 * LifecycleStateChanged represents a notification of state change in a component
 *
 * @param publisher the reference of component's supervisor for which the state changed
 * @param state the new state the component went into
 */
case class LifecycleStateChanged(publisher: ActorRef[ComponentMessage], state: SupervisorLifecycleState) extends TMTSerializable
