package csw.alarm.client

import akka.actor.typed.ActorSystem
import com.typesafe.config.ConfigFactory
import csw.alarm.api.javadsl.IAlarmService
import csw.alarm.api.scaladsl.{AlarmAdminService, AlarmService}
import csw.alarm.client.internal.commons.Settings
import csw.alarm.client.internal.commons.serviceresolver.{
  AlarmServiceHostPortResolver,
  AlarmServiceLocationResolver,
  AlarmServiceResolver
}
import csw.alarm.client.internal.redis.RedisConnectionsFactory
import csw.alarm.client.internal.{AlarmServiceImpl, JAlarmServiceImpl}
import csw.location.api.javadsl.ILocationService
import csw.location.api.scaladsl.LocationService
import io.lettuce.core.RedisClient
import romaine.RomaineFactory

import scala.concurrent.ExecutionContext

/**
 * Factory to create AlarmService
 */
class AlarmServiceFactory(redisClient: RedisClient = RedisClient.create()) {

  /**
   * A java helper to construct AlarmServiceFactory
   * @return
   */
  def this() = this(RedisClient.create())

  /**
   * Creates [[csw.alarm.api.scaladsl.AlarmAdminService]] instance for admin users using [[csw.location.api.scaladsl.LocationService]]
   *
   * @param locationService instance which will be used to resolve the location of alarm server
   * @param system an actor system required for underlying actors
   * @return an instance of [[csw.alarm.api.scaladsl.AlarmAdminService]]
   */
  def makeAdminApi(locationService: LocationService)(implicit system: ActorSystem[_]): AlarmAdminService = {
    implicit val ec: ExecutionContext = system.executionContext
    alarmService(new AlarmServiceLocationResolver(locationService))
  }

  /**
   * Creates [[csw.alarm.api.scaladsl.AlarmAdminService]] instance for admin users using host port of alarm server
   *
   * @param host of the alarm server
   * @param port on which alarm server is running
   * @param system an actor system required for underlying actors
   * @return an instance of [[csw.alarm.api.scaladsl.AlarmAdminService]]
   */
  def makeAdminApi(host: String, port: Int)(implicit system: ActorSystem[_]): AlarmAdminService = {
    implicit val ec: ExecutionContext = system.executionContext
    alarmService(new AlarmServiceHostPortResolver(host, port))
  }

  /**
   * Creates [[csw.alarm.api.scaladsl.AlarmService]] instance for non admin users using [[csw.location.api.scaladsl.LocationService]]
   *
   * @param locationService instance which will be used to resolve the location of alarm server
   * @param system an actor system required for underlying actors
   * @return an instance of [[csw.alarm.api.scaladsl.AlarmService]]
   */
  def makeClientApi(locationService: LocationService)(implicit system: ActorSystem[_]): AlarmService =
    makeAdminApi(locationService)

  /**
   * Creates [[csw.alarm.api.scaladsl.AlarmService]] instance for non admin users using host and port of alarm server
   *
   * @param host of the alarm server
   * @param port on which alarm server is running
   * @param system an actor system required for underlying actors
   * @return an instance of [[csw.alarm.api.scaladsl.AlarmService]]
   */
  def makeClientApi(host: String, port: Int)(implicit system: ActorSystem[_]): AlarmService = makeAdminApi(host, port)

  /**
   * Creates [[csw.alarm.api.javadsl.IAlarmService]] instance for non admin users using [[csw.location.api.javadsl.ILocationService]]
   *
   * @param locationService instance which will be used to resolve the location of alarm server
   * @param system an actor system required for underlying actors
   * @return an instance of [[csw.alarm.api.javadsl.IAlarmService]]
   */
  def jMakeClientApi(locationService: ILocationService, system: ActorSystem[_]): IAlarmService =
    new JAlarmServiceImpl(makeAdminApi(locationService.asScala)(system))

  /**
   * Creates [[csw.alarm.api.javadsl.IAlarmService]] instance for non admin users using host and port of alarm server
   *
   * @param host of the alarm server
   * @param port on which alarm server is running
   * @param system an actor system required for underlying actors
   * @return an instance of [[csw.alarm.api.javadsl.IAlarmService]]
   */
  def jMakeClientApi(host: String, port: Int, system: ActorSystem[_]): IAlarmService =
    new JAlarmServiceImpl(makeAdminApi(host, port)(system))

  /************ INTERNAL ************/
  private def alarmService(alarmServiceResolver: AlarmServiceResolver)(implicit system: ActorSystem[_], ec: ExecutionContext) = {
    val settings = new Settings(ConfigFactory.load())
    val redisConnectionsFactory =
      new RedisConnectionsFactory(alarmServiceResolver, settings.masterId, new RomaineFactory(redisClient))
    new AlarmServiceImpl(redisConnectionsFactory, settings)
  }

  private[alarm] def makeAlarmImpl(locationService: LocationService)(implicit system: ActorSystem[_]) = {
    implicit val ec: ExecutionContext = system.executionContext
    alarmService(new AlarmServiceLocationResolver(locationService))
  }
}
