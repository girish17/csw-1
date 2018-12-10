package csw.time.api.scaladsl

import java.time.Duration

import csw.time.api.models.Cancellable
import csw.time.api.models.TMTTime.{TAITime, UTCTime}

trait TimeService {
  def utcTime(): UTCTime

  def taiTime(): TAITime

  def toUTC(taiInstant: TAITime): UTCTime
  def toTAI(utcInstant: UTCTime): TAITime

  def scheduleOnce(startTime: TAITime)(task: Runnable): Cancellable

  def schedulePeriodically(duration: Duration)(task: Runnable): Cancellable
  def schedulePeriodically(startTime: TAITime, duration: Duration)(task: Runnable): Cancellable

  private[time] def setTaiOffset(offset: Int): Unit
}
