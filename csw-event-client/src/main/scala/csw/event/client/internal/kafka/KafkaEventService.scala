package csw.event.client.internal.kafka

import java.util.UUID

import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, ProducerSettings}
import akka.stream.Materializer
import csw.event.api.scaladsl.{EventPublisher, EventService, EventSubscriber}
import csw.event.client.internal.commons.EventPublisherImpl
import csw.event.client.internal.commons.serviceresolver.EventServiceResolver

import scala.concurrent.{ExecutionContext, Future}

/**
 * Implementation of [[csw.event.api.scaladsl.EventService]] which provides handle to [[csw.event.api.scaladsl.EventPublisher]]
 * and [[csw.event.api.scaladsl.EventSubscriber]] backed by Kafka
 *
 * @param eventServiceResolver to get the connection information of event service
 * @param actorSystem actor system to be used by Producer and Consumer API of akka-stream-kafka
 * @param mat the materializer to be used for materializing underlying streams
 */
class KafkaEventService(eventServiceResolver: EventServiceResolver)(implicit actorSystem: ActorSystem, mat: Materializer)
    extends EventService {

  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  override def makeNewPublisher(): EventPublisher   = new EventPublisherImpl(new KafkaPublishApi(producerSettings))
  override def makeNewSubscriber(): EventSubscriber = new KafkaSubscriber(consumerSettings)

  // resolve event service every time before creating a new publisher
  private def producerSettings: Future[ProducerSettings[String, Array[Byte]]] = eventServiceResolver.uri().map { uri ⇒
    ProducerSettings(actorSystem, None, None).withBootstrapServers(s"${uri.getHost}:${uri.getPort}")
  }

  // resolve event service every time before creating a new subscriber
  private def consumerSettings: Future[ConsumerSettings[String, Array[Byte]]] = eventServiceResolver.uri().map { uri ⇒
    ConsumerSettings(actorSystem, None, None)
      .withBootstrapServers(s"${uri.getHost}:${uri.getPort}")
      .withGroupId(UUID.randomUUID().toString)
  }

}
