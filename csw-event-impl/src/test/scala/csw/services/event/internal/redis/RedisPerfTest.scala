package csw.services.event.internal.redis

import akka.actor.ActorSystem
import com.github.sebruck.EmbeddedRedis
import csw.common.commons.CoordinatedShutdownReasons.TestFinishedReason
import csw.services.event.RedisFactory
import csw.services.event.internal.commons.Wiring
import csw.services.event.internal.perf.EventServicePerfFramework
import csw.services.event.internal.{RateAdapterStage, RateLimiterStage}
import csw.services.location.scaladsl.LocationService
import io.lettuce.core.RedisClient
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import redis.embedded.RedisServer

class RedisPerfTest extends FunSuite with Matchers with BeforeAndAfterAll with EmbeddedRedis with MockitoSugar {
  private val redisPort = 6379
  private val redis     = RedisServer.builder().setting("bind 127.0.0.1").port(redisPort).build()

  private implicit val actorSystem: ActorSystem = ActorSystem()
  private val redisClient                       = RedisClient.create()
  private val wiring                            = new Wiring(actorSystem)
  private val redisFactory                      = new RedisFactory(redisClient, mock[LocationService], wiring)
  private val publisher                         = redisFactory.publisher("localhost", redisPort)
  private val subscriber                        = redisFactory.subscriber("localhost", redisPort)
  private val framework                         = new EventServicePerfFramework(publisher, subscriber)

  override def beforeAll(): Unit = {
    redis.start()
  }

  override def afterAll(): Unit = {
    redisClient.shutdown()
    redis.stop()
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
