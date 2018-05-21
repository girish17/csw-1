package csw.services.event.internal.redis

import akka.Done
import akka.actor.Cancellable
import akka.stream._
import akka.stream.scaladsl.Source
import csw.messages.events.{Event, EventKey}
import csw.services.event.exceptions.PublishFailedException
import csw.services.event.internal.pubsub.{EventPublisherUtil, JEventPublisher}
import csw.services.event.javadsl.IEventPublisher
import csw.services.event.scaladsl.EventPublisher
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.{RedisClient, RedisURI}

import scala.async.Async._
import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class RedisPublisher(
    redisURI: RedisURI,
    redisClient: RedisClient,
    eventPublisherUtil: EventPublisherUtil
)(implicit ec: ExecutionContext, mat: Materializer)
    extends EventPublisher {

  private val parallelism = 1

  private lazy val asyncConnectionF: Future[RedisAsyncCommands[EventKey, Event]] =
    redisClient.connectAsync(EventServiceCodec, redisURI).toScala.map(_.async())

  override def publish(event: Event): Future[Done] =
    async {
      val commands = await(asyncConnectionF)
      val publishF = commands.publish(event.eventKey, event).toScala
      await(publishF)
      commands.set(event.eventKey, event) // set will run in independent of publish
      Done
    } recover {
      case NonFatal(_) ⇒ throw PublishFailedException(event)
    }

  override def publish[Mat](source: Source[Event, Mat]): Mat =
    eventPublisherUtil.publishFromSource(source, parallelism, publish, None)

  override def publish[Mat](source: Source[Event, Mat], onError: Event ⇒ Unit): Mat =
    eventPublisherUtil.publishFromSource(source, parallelism, publish, Some(onError))

  override def publish(eventGenerator: ⇒ Event, every: FiniteDuration): Cancellable =
    publish(eventPublisherUtil.eventSource(eventGenerator, every))

  override def publish(eventGenerator: ⇒ Event, every: FiniteDuration, onError: Event ⇒ Unit): Cancellable =
    publish(eventPublisherUtil.eventSource(eventGenerator, every), onError)

  override def shutdown(): Future[Done] = asyncConnectionF.flatMap(_.quit().toScala).map(_ ⇒ Done)

  override def asJava: IEventPublisher = new JEventPublisher(this)
}
