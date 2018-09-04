package csw.services.alarm.client.internal.services

import akka.Done
import akka.actor.ActorSystem
import akka.actor.typed.ActorRef
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import csw.services.alarm.api.exceptions.{InactiveAlarmException, InvalidSeverityException, KeyNotFoundException}
import csw.services.alarm.api.internal._
import csw.services.alarm.api.models.FullAlarmSeverity.Disconnected
import csw.services.alarm.api.models.Key.AlarmKey
import csw.services.alarm.api.models.{AlarmSeverity, FullAlarmSeverity, Key}
import csw.services.alarm.api.scaladsl.AlarmSubscription
import csw.services.alarm.client.internal.AlarmServiceLogger
import csw.services.alarm.client.internal.commons.Settings
import csw.services.alarm.client.internal.redis.RedisConnectionsFactory
import reactor.core.publisher.FluxSink.OverflowStrategy
import romaine.extensions.SourceExtensions.RichSource
import romaine.reactive.RedisResult

import scala.async.Async.{async, await}
import scala.concurrent.Future

trait SeverityServiceModule extends SeverityService {
  self: MetadataService with StatusService ⇒

  val redisConnectionsFactory: RedisConnectionsFactory
  def settings: Settings
  implicit val actorSystem: ActorSystem
  import redisConnectionsFactory._

  private val log = AlarmServiceLogger.getLogger

  private implicit lazy val mat: Materializer = ActorMaterializer()

  final override def setSeverity(key: AlarmKey, severity: AlarmSeverity): Future[Done] = async {
    await(updateStatusForSeverity(key, severity))
    await(setCurrentSeverity(key, severity))
  }

  final override def getAggregatedSeverity(key: Key): Future[FullAlarmSeverity] = async {
    log.debug(s"Get aggregated severity for alarm [${key.value}]")

    val activeAlarms   = await(getActiveAlarmKeys(key))
    val severityKeys   = activeAlarms.map(SeverityKey.fromMetadataKey)
    val severityValues = await(severityApi.mget(severityKeys))
    val severityList   = severityValues.map(_.value)
    aggregratorByMax(severityList)
  }

  final override def subscribeAggregatedSeverityCallback(key: Key, callback: FullAlarmSeverity ⇒ Unit): AlarmSubscription = {
    log.debug(s"Subscribe aggregated severity for alarm [${key.value}] with a callback")
    subscribeAggregatedSeverity(key).to(Sink.foreach(callback)).run()
  }

  final override def subscribeAggregatedSeverityActorRef(key: Key, actorRef: ActorRef[FullAlarmSeverity]): AlarmSubscription = {
    log.debug(s"Subscribe aggregated severity for alarm [${key.value}] with an actor")
    subscribeAggregatedSeverityCallback(key, actorRef ! _)
  }

  final override def getCurrentSeverity(key: AlarmKey): Future[FullAlarmSeverity] = async {
    log.debug(s"Getting severity for alarm [${key.value}]")

    if (await(metadataApi.exists(key))) await(severityApi.get(key)).getOrElse(Disconnected)
    else logAndThrow(KeyNotFoundException(key))
  }

  private[alarm] def setCurrentSeverity(key: AlarmKey, severity: AlarmSeverity): Future[Done] = async {
    log.debug(
      s"Setting severity [${severity.name}] for alarm [${key.value}] with expire timeout [${settings.severityTTLInSeconds}] seconds"
    )

    // get alarm metadata
    val alarm = await(getMetadata(key))

    // validate if the provided severity is supported by this alarm
    if (!alarm.allSupportedSeverities.contains(severity))
      logAndThrow(InvalidSeverityException(key, alarm.allSupportedSeverities, severity))

    // set the severity of the alarm so that it does not transition to `Disconnected` state
    log.info(s"Updating current severity [${severity.name}] in alarm store")
    await(severityApi.setex(key, settings.severityTTLInSeconds, severity))
  }

  // PatternMessage gives three values:
  // pattern: e.g  __keyspace@0__:status.nfiraos.*.*,
  // channel: e.g. __keyspace@0__:status.nfiraos.trombone.tromboneAxisLowLimitAlarm,
  // message: event type as value: e.g. set, expire, expired
  private[alarm] def subscribeAggregatedSeverity(key: Key): Source[FullAlarmSeverity, AlarmSubscription] = {
    // create new connection for every client
    import csw.services.alarm.client.internal.AlarmCodec._
    val keySpaceApi = redisKeySpaceApi(severityApi)

    val severitySourceF = async {
      val metadataKeys       = await(getActiveAlarmKeys(key))
      val activeSeverityKeys = metadataKeys.map(SeverityKey.fromMetadataKey)
      val currentSeverities  = await(severityApi.mget(activeSeverityKeys)).map(result ⇒ result.key → result.value).toMap

      keySpaceApi
        .watchKeyspaceValue(activeSeverityKeys, OverflowStrategy.LATEST)
        .scan(currentSeverities) {
          case (data, RedisResult(severityKey, mayBeSeverity)) ⇒ data + (severityKey → mayBeSeverity)
        }
        .map(data => aggregratorByMax(data.values))
        .distinctUntilChanged
    }

    Source
      .fromFutureSource(severitySourceF)
      .mapMaterializedValue { mat =>
        new AlarmSubscription {
          override def unsubscribe(): Future[Done] = mat.flatMap(_.unsubscribe())
          override def ready(): Future[Done]       = mat.flatMap(_.ready())
        }
      }
  }

  private def getActiveAlarmKeys(key: Key): Future[List[MetadataKey]] = async {

    val metadataKeys = await(metadataApi.keys(key))
    if (metadataKeys.isEmpty) logAndThrow(KeyNotFoundException(key))

    val keys = await(metadataApi.mget(metadataKeys)).collect {
      case RedisResult(metadataKey, Some(metadata)) if metadata.isActive ⇒ metadataKey
    }

    if (keys.isEmpty) logAndThrow(InactiveAlarmException(key))
    else keys
  }

  private def aggregratorByMax(iterable: Iterable[Option[FullAlarmSeverity]]): FullAlarmSeverity =
    iterable.map(x => if (x.isEmpty) Disconnected else x.get).maxBy(_.level)

  private def logAndThrow(runtimeException: RuntimeException) = {
    log.error(runtimeException.getMessage, ex = runtimeException)
    throw runtimeException
  }
}
