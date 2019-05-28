package csw.alarm.api.internal
import akka.Done
import csw.alarm.api.models.Key.AlarmKey
import csw.alarm.api.models.{AlarmStatus, FullAlarmSeverity}

import scala.concurrent.Future

private[alarm] trait StatusService {

  /**
   * Fetches the status for the given alarm
   *
   * @param alarmKey represents a unique alarm in alarm store
   * @return a future which completes with status or fails with [[csw.alarm.api.exceptions.KeyNotFoundException]]
   */
  def getStatus(alarmKey: AlarmKey): Future[AlarmStatus]

  /**
   * Sets acknowledgement status to acknowledged
   *
   * @param alarmKey represents a unique alarm in alarm store
   * @return a future which completes alarm is acknowledged successfully or fails with [[csw.alarm.api.exceptions.KeyNotFoundException]]
   */
  def acknowledge(alarmKey: AlarmKey): Future[Done]

  /**
   * Sets the latched severity to current severity and acknowledgement status to acknowledged for the given alarm
   *
   * @param alarmKey represents a unique alarm in alarm store
   * @return a future which completes when alarm is reset successfully or fails with [[csw.alarm.api.exceptions.KeyNotFoundException]]
   */
  def reset(alarmKey: AlarmKey): Future[Done]

  /**
   * Sets the shelve status of the given alarm to shelved
   *
   * @note alarms that are shelved will automatically be unshelved at a specific time (currently configured at 8 AM local time)
   *       if not done explicitly. This time is configurable e.g csw-alarm.shelve-timeout = h:m:s a .
   *       Also, shelved alarms are considered in aggregation of the severity and health.
   * @param alarmKey represents a unique alarm in alarm store
   * @return a future which completes when alarm is shelved successfully or fails with [[csw.alarm.api.exceptions.KeyNotFoundException]]
   **/
  def shelve(alarmKey: AlarmKey): Future[Done]

  /**
   * Sets the shelve status of the given alarm to unshelved
   *
   * @param alarmKey represents a unique alarm in alarm store
   * @return a future which completes when the alarm is unshelved successfully or fails with [[csw.alarm.api.exceptions.KeyNotFoundException]]
   */
  def unshelve(alarmKey: AlarmKey): Future[Done]

  /**
   * Latches the severity to Disconnected.
   * Also updates the time to current time if alarm was not already latched to disconnected.
   * Also, updates the acknowledgement status if required.
   *
   * @param alarmKey        Key of the alarm which needs to be latched to Disconnected status
   * @param currentSeverity current heartbeat severity of the alarm
   * @note This method is expected to be called from alarm server when it receives removed event for any alarm
   */
  def latchToDisconnected(alarmKey: AlarmKey, currentSeverity: FullAlarmSeverity): Future[Done]

  private[alarm] def unacknowledge(key: AlarmKey): Future[Done]
  private[alarm] def setStatus(alarmKey: AlarmKey, alarmStatus: AlarmStatus): Future[Done]
  private[alarm] def setStatus(statusMap: Map[AlarmKey, AlarmStatus]): Future[Done]
  private[alarm] def clearAllStatus(): Future[Done]

  private[alarm] def updateStatusForSeverity(
      key: AlarmKey,
      currentSeverity: FullAlarmSeverity,
      newSeverity: FullAlarmSeverity
  ): Future[Done]
}
