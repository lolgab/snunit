package snunit

import scala.concurrent.duration._
import scala.scalanative.loop._

private[snunit] object EventPollingExecutorScheduler {
  def monitorReads(fd: Int, cb: Runnable): Runnable = {
    val poll = Poll(fd)
    poll.startRead(_ => cb.run())
    () => poll.stop()
  }

  private val zero = FiniteDuration(0, SECONDS)

  def execute(runnable: Runnable): Runnable = {
    val timer = Timer.timeout(zero)(() => runnable.run())

    () => timer.clear()
  }
}
