package csw.services.event.internal.pubsub

import java.util.concurrent.CompletableFuture
import java.util.function.{BiConsumer, Supplier}

import akka.Done
import akka.actor.Cancellable
import akka.stream.javadsl.Source
import csw.messages.events.Event
import csw.services.event.exceptions.PublishFailed
import csw.services.event.javadsl.IEventPublisher
import csw.services.event.scaladsl.EventPublisher

import scala.compat.java8.FunctionConverters.{enrichAsScalaFromBiConsumer, enrichAsScalaFromSupplier}
import scala.compat.java8.FutureConverters.FutureOps
import scala.concurrent.duration.FiniteDuration

abstract class JBaseEventPublisher(eventPublisher: EventPublisher) extends IEventPublisher {
  override def publish(event: Event): CompletableFuture[Done] = eventPublisher.publish(event).toJava.toCompletableFuture

  override def publish[Mat](source: Source[Event, Mat]): Mat = eventPublisher.publish(source.asScala)

  override def publish[Mat](source: Source[Event, Mat], onError: BiConsumer[Event, PublishFailed]): Mat =
    eventPublisher.publish(source.asScala, onError.asScala)

  override def publish(eventGenerator: Supplier[Event], every: FiniteDuration): Cancellable =
    eventPublisher.publish(eventGenerator.asScala.apply(), every)

  override def publish(
      eventGenerator: Supplier[Event],
      every: FiniteDuration,
      onError: BiConsumer[Event, PublishFailed]
  ): Cancellable = eventPublisher.publish(eventGenerator.asScala.apply(), every, onError.asScala)

  override def shutdown(): CompletableFuture[Done] = eventPublisher.shutdown().toJava.toCompletableFuture

  def asScala: EventPublisher = eventPublisher
}
