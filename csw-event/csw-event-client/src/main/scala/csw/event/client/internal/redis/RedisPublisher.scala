package csw.event.client.internal.redis

import akka.Done
import akka.actor.Cancellable
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import csw.event.api.exceptions.PublishFailure
import csw.event.api.scaladsl.EventPublisher
import csw.event.client.internal.commons.EventPublisherUtil
import csw.params.core.generics.KeyType.StringKey
import csw.params.core.models.Subsystem
import csw.params.events.{Event, EventKey, SystemEvent}
import csw.time.core.models.TMTTime
import csw.time.core.util.TMTTimeUtil.delayFrom
import io.lettuce.core.{RedisClient, RedisURI}
import romaine.RomaineFactory
import romaine.async.RedisAsyncApi

import scala.async.Async._
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * An implementation of [[csw.event.api.scaladsl.EventPublisher]] API which uses Redis as the provider for publishing
 * and subscribing events.
 *
 * @param redisURI    future containing connection details for the Redis/Sentinel connections.
 * @param redisClient redis client available from lettuce
 * @param mat         the materializer to be used for materializing underlying streams
 */
class RedisPublisher(redisURI: Future[RedisURI], redisClient: RedisClient)(implicit mat: Materializer, ec: ExecutionContext)
    extends EventPublisher {

  // inorder to preserve the order of publishing events, the parallelism level is maintained to 1
  private val parallelism                         = 1
  private val defaultInitialDelay: FiniteDuration = 0.millis
  private val eventPublisherUtil                  = new EventPublisherUtil()
  private val romaineFactory                      = new RomaineFactory(redisClient)
  import EventRomaineCodecs._

  private val asyncApi: RedisAsyncApi[String, Event] = romaineFactory.redisAsyncApi(redisURI)

  private val streamTermination: Future[Done] = eventPublisherUtil.streamTermination(publishInternal)

  publishInitializationEvent()

  override def publish(event: Event): Future[Done] = eventPublisherUtil.publish(event, streamTermination.isCompleted)

  private def publishInternal(event: Event): Future[Done] =
    async {
      await(asyncApi.publish(event.eventKey.key, event))
      set(event, asyncApi) // set will run independent of publish
      Done
    } recover {
      case NonFatal(ex) ⇒
        val failure = PublishFailure(event, ex)
        eventPublisherUtil.logError(failure)
        throw failure
    }

  override def publish[Mat](source: Source[Event, Mat]): Mat =
    eventPublisherUtil.publishFromSource(source, parallelism, publishInternal, None)

  override def publish[Mat](source: Source[Event, Mat], onError: PublishFailure ⇒ Unit): Mat =
    eventPublisherUtil.publishFromSource(source, parallelism, publishInternal, Some(onError))

  override def publish(eventGenerator: => Option[Event], every: FiniteDuration): Cancellable =
    publish(eventPublisherUtil.getEventSource(Future.successful(eventGenerator), defaultInitialDelay, every))

  override def publish(eventGenerator: => Option[Event], startTime: TMTTime, every: FiniteDuration): Cancellable =
    publish(eventPublisherUtil.getEventSource(Future.successful(eventGenerator), delayFrom(startTime), every))

  override def publish(eventGenerator: ⇒ Option[Event], every: FiniteDuration, onError: PublishFailure ⇒ Unit): Cancellable =
    publish(eventPublisherUtil.getEventSource(Future.successful(eventGenerator), defaultInitialDelay, every), onError)

  override def publish(
      eventGenerator: => Option[Event],
      startTime: TMTTime,
      every: FiniteDuration,
      onError: PublishFailure => Unit
  ): Cancellable =
    publish(eventPublisherUtil.getEventSource(Future.successful(eventGenerator), delayFrom(startTime), every), onError)

  override def publishAsync(eventGenerator: ⇒ Future[Option[Event]], every: FiniteDuration): Cancellable =
    publish(eventPublisherUtil.getEventSource(eventGenerator, defaultInitialDelay, every))

  override def publishAsync(eventGenerator: => Future[Option[Event]], startTime: TMTTime, every: FiniteDuration): Cancellable =
    publish(eventPublisherUtil.getEventSource(eventGenerator, delayFrom(startTime), every))

  override def publishAsync(eventGenerator: ⇒ Future[Option[Event]],
                            every: FiniteDuration,
                            onError: PublishFailure ⇒ Unit): Cancellable =
    publish(eventPublisherUtil.getEventSource(eventGenerator, defaultInitialDelay, every), onError)

  override def publishAsync(
      eventGenerator: => Future[Option[Event]],
      startTime: TMTTime,
      every: FiniteDuration,
      onError: PublishFailure => Unit
  ): Cancellable =
    publish(eventPublisherUtil.getEventSource(eventGenerator, delayFrom(startTime), every), onError)

  override def shutdown(): Future[Done] = {
    eventPublisherUtil.shutdown()
    asyncApi.quit().map(_ ⇒ Done)
  }

  private def set(event: Event, commands: RedisAsyncApi[String, Event]): Future[Done] =
    commands.set(event.eventKey.key, event).recover { case NonFatal(_) ⇒ Done }

  private def publishInitializationEvent() = {
    val initParam = StringKey.make("InitKey").set("IGNORE: Redis publisher initialization")
    val key       = EventKey(s"${Subsystem.TEST}.init")
    val event     = SystemEvent(key.source, key.eventName, Set(initParam))
    Await.result(publish(event), 10.seconds)
  }

}
