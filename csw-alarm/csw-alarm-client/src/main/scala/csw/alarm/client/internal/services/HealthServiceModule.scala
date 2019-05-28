package csw.alarm.client.internal.services

import akka.actor.typed
import akka.actor.typed.{ActorRef, _}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.typed.scaladsl
import csw.alarm.api.internal.{HealthService, SeverityService}
import csw.alarm.api.models.{AlarmHealth, Key}
import csw.alarm.api.scaladsl.AlarmSubscription
import csw.alarm.client.internal.AlarmServiceLogger
import csw.alarm.client.internal.redis.RedisConnectionsFactory
import romaine.extensions.SourceExtensions.RichSource

import scala.concurrent.Future

private[client] trait HealthServiceModule extends HealthService {
  self: SeverityService ⇒

  val redisConnectionsFactory: RedisConnectionsFactory
  implicit val actorSystem: typed.ActorSystem[_]
  import redisConnectionsFactory._

  private val log                             = AlarmServiceLogger.getLogger
  private implicit lazy val mat: Materializer = scaladsl.ActorMaterializer()

  final override def getAggregatedHealth(key: Key): Future[AlarmHealth] = {
    log.debug(s"Get aggregated health for alarm [${key.value}]")
    getAggregatedSeverity(key).map(AlarmHealth.fromSeverity)
  }

  final override def subscribeAggregatedHealthCallback(key: Key, callback: AlarmHealth ⇒ Unit): AlarmSubscription = {
    subscribeAggregatedHealth(key).to(Sink.foreach(callback)).run()
  }

  final override def subscribeAggregatedHealthActorRef(key: Key, actorRef: ActorRef[AlarmHealth]): AlarmSubscription = {
    log.debug(s"Subscribe aggregated health for alarm [${key.value}] with an actor")
    subscribeAggregatedHealthCallback(key, actorRef ! _)
  }

  private[alarm] def subscribeAggregatedHealth(key: Key): Source[AlarmHealth, AlarmSubscription] = {
    log.debug(s"Subscribe aggregated health for alarm [${key.value}] with a callback")
    subscribeAggregatedSeverity(key).map(AlarmHealth.fromSeverity).distinctUntilChanged
  }
}
