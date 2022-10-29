package snunit

import scala.concurrent.duration._
import scala.scalanative.loop._

private[snunit] object EventPollingExecutorScheduler {
  def monitorReads(fd: Int, cb: Runnable): Runnable = {
    val poll = Poll(fd)
    poll.startRead(_ => cb.run())
    () => poll.stop()
  }

  def execute(runnable: Runnable): Unit = {
    scala.concurrent.ExecutionContext.global.execute(runnable)
  }
}
