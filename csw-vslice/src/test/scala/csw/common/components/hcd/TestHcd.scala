package csw.common.components.hcd

import akka.typed.scaladsl.{Actor, ActorContext}
import akka.typed.{ActorRef, Behavior}
import csw.common.framework.models.{HcdComponentLifecycleMessage, HcdMsg, ToComponentLifecycleMessage}
import csw.common.framework.scaladsl.HcdActor
import csw.param.Parameters

import scala.concurrent.Future

object TestHcd {
  def behaviour(supervisor: ActorRef[HcdComponentLifecycleMessage]): Behavior[Nothing] =
    Actor.mutable[HcdMsg](ctx ⇒ new TestHcd(ctx, supervisor)).narrow
}

class TestHcd(ctx: ActorContext[HcdMsg], supervisor: ActorRef[HcdComponentLifecycleMessage])
    extends HcdActor[TestHcdMessage](ctx, supervisor) {

  override def initialize(): Future[Unit] = Future.unit

  override def onRun(): Unit = Unit

  override def onShutdown(): Unit = Unit

  override def onShutdownComplete(): Unit = Unit

  override def onLifecycle(x: ToComponentLifecycleMessage): Unit = Unit

  override def onSetup(sc: Parameters.Setup): Unit = Unit

  override def onDomainMsg(msg: TestHcdMessage): Unit = Unit
}
