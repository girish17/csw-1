package csw.services.event.perf

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import scala.concurrent.duration.{DurationLong, FiniteDuration}

class TestConfigs(config: Config) {

  //################### Common Configuration ###################
  val elements: Int = config.getInt("csw.test.EventServicePerfTest.publish-frequency.elements")
  val per: FiniteDuration = {
    val d = config.getDuration("csw.test.EventServicePerfTest.publish-frequency.per")
    FiniteDuration(d.toNanos, TimeUnit.NANOSECONDS)
  }

  val publishFrequency: FiniteDuration = (per.toMillis / elements).millis

  val warmupMsgs: Int             = config.getInt("csw.test.EventServicePerfTest.warmup")
  val burstSize: Int              = config.getInt("csw.test.EventServicePerfTest.burst-size")
  val totalMessagesFactor: Double = config.getDouble("csw.test.EventServicePerfTest.totalMessagesFactor")

  val shareConnection: Boolean = config.getBoolean("csw.test.EventServicePerfTest.one-connection-per-jvm")

  //################### Redis Configuration ###################
  val redisEnabled: Boolean = config.getBoolean("csw.test.EventServicePerfTest.redis-enabled")
  val redisHost: String     = config.getString("csw.test.EventServicePerfTest.redis.host")
  val redisPort: Int        = config.getInt("csw.test.EventServicePerfTest.redis.port")

  //################### Kafka Configuration ###################
  val kafkaHost: String = config.getString("csw.test.EventServicePerfTest.kafka.host")
  val kafkaPort: Int    = config.getInt("csw.test.EventServicePerfTest.kafka.port")

}