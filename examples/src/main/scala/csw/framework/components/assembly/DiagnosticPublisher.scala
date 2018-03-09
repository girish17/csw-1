package csw.framework.components.assembly

import java.util.Optional

import akka.typed.scaladsl.Actor.MutableBehavior
import akka.typed.scaladsl.{Actor, ActorContext}
import akka.typed.{ActorRef, Behavior}
import csw.messages.scaladsl.ComponentMessage

import scala.compat.java8.OptionConverters._

trait DiagnosticPublisherMessages

object DiagnosticsPublisher {
  def make(
      runningIn: Option[ActorRef[ComponentMessage]],
      worker: Option[ActorRef[WorkerActorMsg]]
  ): Behavior[DiagnosticPublisherMessages] =
    Actor.mutable(ctx ⇒ new DiagnosticsPublisher(ctx, runningIn, worker))

  def jMake(
      runningIn: Optional[ActorRef[ComponentMessage]],
      worker: Optional[ActorRef[WorkerActorMsg]]
  ): Behavior[DiagnosticPublisherMessages] =
    Actor.mutable(ctx ⇒ new DiagnosticsPublisher(ctx, runningIn.asScala, worker.asScala))

}

class DiagnosticsPublisher(
    ctx: ActorContext[DiagnosticPublisherMessages],
    runningIn: Option[ActorRef[ComponentMessage]],
    worker: Option[ActorRef[WorkerActorMsg]]
) extends MutableBehavior[DiagnosticPublisherMessages] {
  override def onMessage(msg: DiagnosticPublisherMessages): Behavior[DiagnosticPublisherMessages] = ???
}
