package csw.event.client.internal.redis

import java.util.concurrent.Executors

import akka.Done
import akka.actor.Cancellable
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import csw.event.api.exceptions.{EventServerNotAvailable, PublishFailure}
import csw.event.api.scaladsl.EventPublisher
import csw.event.client.internal.commons.EventPublisherUtil
import csw.params.events.Event
import io.lettuce.core.{RedisClient, RedisURI}
import romaine.RomaineFactory
import romaine.async.RedisAsyncApi
import romaine.exceptions.RedisServerNotAvailable

import scala.async.Async._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * An implementation of [[csw.event.api.scaladsl.EventPublisher]] API which uses Redis as the provider for publishing
 * and subscribing events.
 *
 * @param redisURI    future containing connection details for the Redis/Sentinel connections.
 * @param redisClient redis client available from lettuce
 * @param mat         the materializer to be used for materializing underlying streams
 */
class RedisPublisher(redisURI: RedisURI, redisClient: RedisClient)(implicit mat: Materializer) extends EventPublisher {

  private implicit val singleThreadedEc: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())

  // inorder to preserve the order of publishing events, the parallelism level is maintained to 1
  private val parallelism        = 1
  private val eventPublisherUtil = new EventPublisherUtil()

  private val romaineFactory = new RomaineFactory(redisClient)

  import EventRomaineCodecs._
  private val asyncApi: RedisAsyncApi[String, Event] = createAsyncApi()

  override def publish(event: Event): Future[Done] =
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
    eventPublisherUtil.publishFromSource(source, parallelism, publish, None)

  override def publish[Mat](source: Source[Event, Mat], onError: PublishFailure ⇒ Unit): Mat =
    eventPublisherUtil.publishFromSource(source, parallelism, publish, Some(onError))

  override def publish(eventGenerator: ⇒ Event, every: FiniteDuration): Cancellable =
    publish(eventPublisherUtil.eventSource(eventGenerator, every))

  override def publish(eventGenerator: ⇒ Event, every: FiniteDuration, onError: PublishFailure ⇒ Unit): Cancellable =
    publish(eventPublisherUtil.eventSource(eventGenerator, every), onError)

  override def publishAsync(eventGenerator: ⇒ Future[Event], every: FiniteDuration): Cancellable =
    publish(eventPublisherUtil.eventSourceAsync(eventGenerator, every))

  override def publishAsync(eventGenerator: ⇒ Future[Event], every: FiniteDuration, onError: PublishFailure ⇒ Unit): Cancellable =
    publish(eventPublisherUtil.eventSourceAsync(eventGenerator, every), onError)

  override def shutdown(): Future[Done] = asyncApi.quit().map(_ ⇒ Done)

  private def set(event: Event, commands: RedisAsyncApi[String, Event]): Future[Done] =
    commands.set(event.eventKey.key, event).recover { case NonFatal(_) ⇒ Done }

  private def createAsyncApi(): RedisAsyncApi[String, Event] =
    try {
      romaineFactory.redisAsyncApi[String, Event](redisURI)
    } catch {
      case RedisServerNotAvailable(ex) => throw EventServerNotAvailable(ex)
    }
}
