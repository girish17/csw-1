package csw.services.event.internal.kafka

import akka.actor.ActorSystem
import csw.common.commons.CoordinatedShutdownReasons.TestFinishedReason
import csw.services.event.KafkaFactory
import csw.services.event.helpers.TestFutureExt.RichFuture
import csw.services.event.internal.commons.Wiring
import csw.services.event.internal.perf.EventServicePerfFramework
import csw.services.event.internal.{RateAdapterStage, RateLimiterStage}
import csw.services.location.scaladsl.LocationService
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

class KafkaPerfTest extends FunSuite with Matchers with BeforeAndAfterAll with EmbeddedKafka with MockitoSugar {
  private val kafkaPort = 6001

  private implicit val actorSystem: ActorSystem = ActorSystem()
  private val pubSubProperties                  = Map("bootstrap.servers" → s"localhost:$kafkaPort")
  private val brokers                           = s"PLAINTEXT://localhost:$kafkaPort"
  private val brokerProperties                  = Map("listeners" → brokers, "advertised.listeners" → brokers)

  private val config = EmbeddedKafkaConfig(customConsumerProperties = pubSubProperties,
                                           customProducerProperties = pubSubProperties,
                                           customBrokerProperties = brokerProperties)

  private val wiring       = new Wiring(actorSystem)
  private val kafkaFactory = new KafkaFactory(mock[LocationService], wiring)
  private val publisher    = kafkaFactory.publisher("localhost", kafkaPort)
  private val subscriber   = kafkaFactory.subscriber("localhost", kafkaPort)
  private val framework    = new EventServicePerfFramework(publisher, subscriber)

  override def beforeAll(): Unit = {
    EmbeddedKafka.start()(config)
  }

  override def afterAll(): Unit = {
    publisher.shutdown().await
    EmbeddedKafka.stop()
    wiring.shutdown(TestFinishedReason)
  }

  ignore("limiter") {
    framework.comparePerf(new RateLimiterStage(_))
  }

  ignore("adapter") {
    framework.comparePerf(new RateAdapterStage(_))
  }

  ignore("throughput-latency") {
    framework.monitorPerf()
  }
}
