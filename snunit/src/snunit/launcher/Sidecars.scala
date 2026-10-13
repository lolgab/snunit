package snunit.launcher

import java.nio.file.{Files, Paths}
import scala.concurrent.duration.*
import scala.scalanative.posix.signal
import scala.scalanative.posix.sys.{wait => sysWait}
import scala.scalanative.unsafe.*
import snunit.config.Sidecar

/** Runs and supervises the [[snunit.config.Sidecar]]s. It has no thread: [[tick]] is called from the launcher loop.
  *
  * @param start
  *   starts a sidecar and returns its pid
  */
private[launcher] final class Sidecars(specs: Seq[Sidecar], start: Sidecar => Int) {
  private final class State(val spec: Sidecar) {
    var pid = 0
    var restarts = 0L
    var startedAt = 0L
    var nextStartAt = 0L
    var delay = Sidecars.MinDelay
    var gone = false // not running and not to be restarted
  }
  private val states = specs.map(new State(_))
  private var stopping = false

  def startAll(): Unit = states.foreach(startOne)

  private def startOne(s: State): Unit =
    try {
      s.pid = start(s.spec)
      s.startedAt = System.nanoTime()
    } catch {
      case e: java.io.IOException =>
        System.err.println(s"snunit: cannot start the sidecar '${s.spec.name}': ${e.getMessage}")
        schedule(s, failed = true)
    }

  private def schedule(s: State, failed: Boolean): Unit = {
    s.pid = 0
    val restart = s.spec.restart match {
      case Sidecar.Restart.Always    => true
      case Sidecar.Restart.OnFailure => failed
      case Sidecar.Restart.Never     => false
    }
    if (restart) {
      // A sidecar that ran for a while was healthy: restart it quickly.
      if (System.nanoTime() - s.startedAt > Sidecars.HealthyAfter.toNanos) s.delay = Sidecars.MinDelay
      s.nextStartAt = System.nanoTime() + s.delay.toNanos
      s.delay = (s.delay * 2).min(Sidecars.MaxDelay)
    } else s.gone = true
  }

  /** Collects the sidecars that exited and starts again the ones that are due. */
  def tick(): Unit =
    if (!stopping) {
      val status = stackalloc[CInt]()
      states.foreach { s =>
        if (s.pid > 0) {
          if (sysWait.waitpid(s.pid, status, sysWait.WNOHANG) == s.pid) {
            val failed = !sysWait.WIFEXITED(!status) || sysWait.WEXITSTATUS(!status) != 0
            System.err.println(
              s"snunit: the sidecar '${s.spec.name}' exited " +
                (if (failed) "with an error" else "normally") + (if (
                                                                   s.spec.restart == Sidecar.Restart.Never || s.spec.restart == Sidecar.Restart.OnFailure && !failed
                                                                 ) ""
                                                                 else ", restarting")
            )
            schedule(s, failed)
          }
        } else if (!s.gone && System.nanoTime() >= s.nextStartAt) {
          s.restarts += 1
          startOne(s)
        }
      }
    }

  def samples: Seq[Sample] =
    states.flatMap { s =>
      val labels = Seq("sidecar" -> s.spec.name)
      Seq(
        Sample("snunit_sidecar_up", "1 if the sidecar process is running.", false, if (s.pid > 0) 1 else 0, labels),
        Sample("snunit_sidecar_restarts_total", "Times the sidecar was started again.", true, s.restarts, labels)
      )
    }

  /** Sends SIGTERM to the running sidecars and waits for them, then kills the ones that didn't exit in time. */
  def stopAll(): Unit = {
    stopping = true
    val running = states.filter(_.pid > 0)
    running.foreach(s => signal.kill(s.pid, signal.SIGTERM))
    val status = stackalloc[CInt]()
    val deadlines = running.map(s => s -> (System.nanoTime() + s.spec.stopTimeout.toNanos)).toMap
    var pending = running
    while (pending.nonEmpty) {
      pending = pending.filter { s =>
        val exited = sysWait.waitpid(s.pid, status, sysWait.WNOHANG) != 0
        if (!exited && System.nanoTime() >= deadlines(s)) {
          signal.kill(s.pid, signal.SIGKILL)
          sysWait.waitpid(s.pid, status, 0)
          false
        } else !exited
      }
      if (pending.nonEmpty) Thread.sleep(20)
    }
    states.foreach(_.pid = 0)
  }

  /** Last resort when the launcher fails. */
  def killAll(): Unit = states.foreach(s => if (s.pid > 0) signal.kill(s.pid, signal.SIGKILL))
}

private[launcher] object Sidecars {
  val MinDelay: FiniteDuration = 1.second
  val MaxDelay: FiniteDuration = 30.seconds
  val HealthyAfter: FiniteDuration = 30.seconds

  /** `command` itself if it has a `/`, otherwise the first executable of that name in the `PATH`. */
  def resolve(command: String): String =
    if (command.contains('/')) command
    else
      sys.env
        .getOrElse("PATH", "/usr/local/bin:/usr/bin:/bin")
        .split(':')
        .iterator
        .filter(_.nonEmpty)
        .map(dir => Paths.get(dir, command))
        .find(p => Files.isRegularFile(p) && Files.isExecutable(p))
        .map(_.toString)
        .getOrElse(throw new java.io.IOException(s"'$command' not found in the PATH"))
}
