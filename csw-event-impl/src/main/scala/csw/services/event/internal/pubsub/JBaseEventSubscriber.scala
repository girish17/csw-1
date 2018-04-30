package csw.services.event.internal.pubsub

import java.util
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

import akka.Done
import akka.actor.typed.ActorRef
import akka.stream.Materializer
import akka.stream.javadsl.Source
import akka.stream.scaladsl.Sink
import csw.messages.events.{Event, EventKey}
import csw.services.event.internal.throttle.RateAdapterStage
import csw.services.event.javadsl.{IEventSubscriber, IEventSubscription}
import csw.services.event.scaladsl.{EventSubscriber, EventSubscription}

import scala.collection.JavaConverters.{asScalaSetConverter, setAsJavaSetConverter}
import scala.compat.java8.FutureConverters.{CompletionStageOps, FutureOps}
import scala.concurrent.duration.FiniteDuration

abstract class JBaseEventSubscriber(eventSubscriber: EventSubscriber) extends IEventSubscriber {

  def subscribe(eventKeys: util.Set[EventKey]): Source[Event, IEventSubscription] =
    eventSubscriber
      .subscribe(eventKeys.asScala.toSet)
      .asJava
      .mapMaterializedValue { eventSubscription: EventSubscription ⇒
        new IEventSubscription {
          override def unsubscribe(): CompletableFuture[Done] = eventSubscription.unsubscribe().toJava.toCompletableFuture

          override def ready(): CompletableFuture[Done] = eventSubscription.ready().toJava.toCompletableFuture
        }
      }

  def subscribe(eventKeys: util.Set[EventKey], every: FiniteDuration): Source[Event, IEventSubscription] =
    subscribe(eventKeys).via(new RateAdapterStage[Event](every))

  def subscribeAsync(
      eventKeys: util.Set[EventKey],
      callback: Event ⇒ CompletableFuture[_],
      mat: Materializer
  ): IEventSubscription = subscribe(eventKeys).asScala.mapAsync(1)(callback(_).toScala).to(Sink.ignore).run()(mat)

  def subscribeAsync(
      eventKeys: util.Set[EventKey],
      callback: Event => CompletableFuture[_],
      every: FiniteDuration,
      mat: Materializer
  ): IEventSubscription = subscribe(eventKeys, every).asScala.mapAsync(1)(callback(_).toScala).to(Sink.ignore).run()(mat)

  def subscribeCallback(eventKeys: util.Set[EventKey], callback: Consumer[Event], mat: Materializer): IEventSubscription =
    subscribe(eventKeys).asScala.to(Sink.foreach(callback.accept)).run()(mat)

  def subscribeCallback(
      eventKeys: util.Set[EventKey],
      callback: Consumer[Event],
      every: FiniteDuration,
      mat: Materializer
  ): IEventSubscription = subscribe(eventKeys, every).asScala.to(Sink.foreach(callback.accept)).run()(mat)

  def subscribeActorRef(eventKeys: util.Set[EventKey], actorRef: ActorRef[Event], mat: Materializer): IEventSubscription =
    subscribeCallback(eventKeys, event => actorRef ! event, mat)

  def subscribeActorRef(
      eventKeys: util.Set[EventKey],
      actorRef: ActorRef[Event],
      every: FiniteDuration,
      mat: Materializer
  ): IEventSubscription = subscribeCallback(eventKeys, event => actorRef ! event, every, mat)

  def get(eventKeys: util.Set[EventKey]): CompletableFuture[util.Set[Event]] =
    eventSubscriber.get(eventKeys.asScala.toSet).toJava.toCompletableFuture.thenApply(_.asJava)

  def get(eventKey: EventKey): CompletableFuture[Event] = eventSubscriber.get(eventKey).toJava.toCompletableFuture

  def asScala: EventSubscriber = eventSubscriber
}
