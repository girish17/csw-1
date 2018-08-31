package csw.services.alarm.client.internal.models

import csw.services.alarm.api.models.Key.AlarmKey
import csw.services.alarm.api.models._

case class Alarm(key: AlarmKey, metadata: AlarmMetadata, status: AlarmStatus, severity: FullAlarmSeverity)
//trait AlarmOrdering extends Ordering[Alarm] {
//  def compare(x: Alarm, y: Alarm): Int =
//}
