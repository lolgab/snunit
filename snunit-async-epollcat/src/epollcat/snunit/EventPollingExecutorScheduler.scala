package epollcat.snunit

import epollcat.unsafe.EpollRuntime
import epollcat.unsafe.EventNotificationCallback

object EventPollingExecutorScheduler {
  val scheduler = EpollRuntime.global.scheduler.asInstanceOf[epollcat.unsafe.EventPollingExecutorScheduler]

  def toEpollcat(cb: snunit.EventPollingExecutorScheduler.EventNotificationCallback): EventNotificationCallback = {
    new EventNotificationCallback {
      protected[epollcat] def notifyEvents(readReady: Boolean, writeReady: Boolean): Unit = {
        cb.notifyEvents(readReady = readReady, writeReady = writeReady)
      }
    }
  }
}
