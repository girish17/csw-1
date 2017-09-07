package csw.common.framework.internal.pubsub

import akka.typed.scaladsl.{Actor, ActorContext}
import akka.typed.{ActorRef, Behavior, Signal, Terminated}
import csw.common.framework.models.PubSub
import csw.common.framework.models.PubSub.{Publish, Subscribe, Unsubscribe}

class PubSubBehavior[T](ctx: ActorContext[PubSub[T]]) extends Actor.MutableBehavior[PubSub[T]] {

  var subscribers: Set[ActorRef[T]] = Set.empty

  override def onMessage(msg: PubSub[T]): Behavior[PubSub[T]] = {
    msg match {
      case Subscribe(ref)   => subscribe(ref)
      case Unsubscribe(ref) => unsubscribe(ref)
      case Publish(data)    => notifySubscribers(data)
    }
    this
  }

  override def onSignal: PartialFunction[Signal, Behavior[PubSub[T]]] = {
    case Terminated(ref) ⇒ unsubscribe(ref.upcast); this
  }

  private def subscribe(actorRef: ActorRef[T]): Unit = {
    if (!subscribers.contains(actorRef)) {
      subscribers += actorRef
      ctx.watch(actorRef)
    }
  }

  private def unsubscribe(actorRef: ActorRef[T]): Unit = {
    subscribers -= actorRef
  }

  protected def notifySubscribers(a: T): Unit = {
    subscribers.foreach(_ ! a)
  }
}
