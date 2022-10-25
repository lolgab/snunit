package snunit.http4s

import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.IORuntimeConfig
import cats.effect.unsafe.Scheduler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.scalanative.loop.Timer

object LoopIORuntime {
  private object LoopScheduler extends Scheduler {
    def monotonicNanos(): Long = System.nanoTime()
    def nowMillis(): Long = System.currentTimeMillis()
    def sleep(delay: FiniteDuration, task: Runnable): Runnable = {
      val timer = Timer.timeout(delay)(() => task.run())

      () => timer.clear()
    }
  }
  val global: IORuntime = IORuntime(
    ExecutionContext.global,
    ExecutionContext.global,
    LoopScheduler,
    () => (),
    IORuntimeConfig()
  )
}
