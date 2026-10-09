package snunit.launcher

import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.PosixFilePermissions
import snunit.config.UnitConfig
import scala.scalanative.libc.errno
import scala.scalanative.libc.stdlib.getenv
import scala.scalanative.libc.string
import scala.scalanative.meta.LinktimeInfo
import scala.scalanative.posix.fcntl
import scala.scalanative.posix.signal
import scala.scalanative.posix.spawn
import scala.scalanative.posix.sys.{wait => sysWait}
import scala.scalanative.posix.unistd
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

/** Makes an SNUnit executable run standalone.
  *
  * When the executable is started directly (not by `unitd`) and contains an embedded `unitd` (see [[EmbeddedUnitd]]),
  * it starts `unitd` with a configuration generated from a [[snunit.config.UnitConfig]] that runs the same executable
  * as the application, then exits when `unitd` exits. When it is started by `unitd` this does nothing.
  *
  * Shutdown:
  *   - SIGTERM, SIGINT and SIGHUP make the server stop gracefully: it stops accepting connections and waits for the
  *     running requests to finish. A second signal stops it without waiting, a third one kills it. The same happens
  *     when [[snunit.config.UnitConfig.shutdownTimeout]] elapses.
  *   - If the launcher is killed in a way it can't handle (SIGKILL, OOM killer...), a small watchdog shell process
  *     stops `unitd` the same way and removes the temporary directory.
  *
  * `unitd` and the watchdog run in their own process group, so that the signals the terminal sends to the foreground
  * process group (Ctrl-C) reach only the launcher, which decides how to shut down.
  */
private[snunit] object Launcher {
  private val UnitInitEnv = c"NXT_UNIT_INIT"

  @extern private object darwin {
    def _NSGetExecutablePath(buf: CString, bufsize: Ptr[CUnsignedInt]): CInt = extern
  }

  @volatile private var unitdPid: Int = 0
  @volatile private var signalsReceived: Int = 0

  /** Only counts the signals: the main loop decides what to do (a signal handler can't do much). */
  private val onShutdownSignal = CFuncPtr1.fromScalaFunction[CInt, Unit] { _ =>
    signalsReceived = signalsReceived + 1
  }

  private val ignoreSignal = CFuncPtr1.fromScalaFunction[CInt, Unit](_ => ())

  def runIfNeeded(config: UnitConfig): Unit =
    if (getenv(UnitInitEnv) == null) {
      EmbeddedUnitd.bytes() match {
        case Some(bytes) => run(bytes, config)
        case None        => () // Not started by unitd and no unitd embedded: nothing we can do.
      }
    }

  /** Real path of the running executable. */
  def selfExecutable(): String =
    if (LinktimeInfo.isMac) Zone {
      val size = alloc[CUnsignedInt]()
      !size = 4096.toUInt
      val buf = alloc[CChar](4096)
      if (darwin._NSGetExecutablePath(buf, size) != 0) throw new Exception("Cannot find the executable path")
      Paths.get(fromCString(buf)).toRealPath().toString
    }
    else Paths.get("/proc/self/exe").toRealPath().toString

  private def run(unitdBytes: Array[Byte], config: UnitConfig): Nothing = {
    // Short path: unix socket paths are limited to ~100 characters.
    val dir = Files.createTempDirectory(Paths.get("/tmp"), "snunit")
    var watchdog: Option[Watchdog] = None
    try {
      val statedir = Files.createDirectory(dir.resolve("statedir"))
      Files.createDirectory(dir.resolve("tmp"))
      writeCertificates(statedir, config)
      Files.write(statedir.resolve("conf.json"), config.toJson(selfExecutable()).getBytes("UTF-8"))

      // Started before unitd, so that there is no window where unitd runs unsupervised.
      watchdog = Watchdog.start(dir, config)

      val unitd = EmbeddedUnitd.executablePath(unitdBytes)
      unitdPid = spawnProcess(
        unitd,
        Seq(
          "--no-daemon",
          "--control",
          s"unix:${dir.resolve("control.sock")}",
          "--pid",
          dir.resolve("unit.pid").toString,
          "--log",
          "/dev/stdout",
          "--statedir",
          statedir.toString,
          "--tmpdir",
          dir.resolve("tmp").toString,
          "--modulesdir",
          dir.resolve("modules").toString
        ),
        newProcessGroup = true
      )
      watchdog.foreach(_.watch(unitdPid))

      // Writing to a watchdog that died must not kill the launcher.
      signal.signal(signal.SIGPIPE, ignoreSignal)
      signal.signal(signal.SIGTERM, onShutdownSignal)
      signal.signal(signal.SIGINT, onShutdownSignal)
      signal.signal(signal.SIGHUP, onShutdownSignal)

      val code = waitForExit(unitdPid, dir, config)
      watchdog.foreach(_.stop())
      watchdog = None
      deleteRecursively(dir)
      sys.exit(code)
    } catch {
      case e: Throwable =>
        // Don't leave unitd running if the launcher can't supervise it.
        if (unitdPid > 0) signal.kill(unitdPid, signal.SIGKILL)
        watchdog.foreach(_.stop())
        deleteRecursively(dir)
        throw e
    }
  }

  /** Waits for `pid` to exit and returns its exit code, handling the shutdown requests.
    *
    * The first signal drains the server: the listeners are removed, so that no new connection is accepted, and when the
    * running connections are done (or [[snunit.config.UnitConfig.shutdownTimeout]] elapsed) `unitd` is asked to quit.
    * Telling `unitd` to quit right away would close the running requests, only the application processes drain. The
    * second signal, or the timeout, stops `unitd` without waiting. After 5 more seconds, or the third signal, it is
    * killed.
    */
  private def waitForExit(pid: Int, unitdDir: Path, config: UnitConfig): Int = {
    val control = new ControlClient(unitdDir.resolve("control.sock").toString)
    val status = stackalloc[CInt]()
    val timeoutNanos = config.shutdownTimeout.toNanos
    val killTimeoutNanos = 5L * 1000 * 1000 * 1000
    var drainStartedAt = 0L
    var drainStep = 0 // 0: not started, 1: listeners removed, waiting for the connections, 2: asked to quit
    var terminatedAt = 0L
    var killed = false
    var nextPollAt = 0L
    // `unitd` keeps running when it rejects the configuration, but without listeners: nothing would ever be served.
    var configurationChecked = false
    var configurationRejected = false
    val startedAt = System.nanoTime()
    var nextCheckAt = 0L
    while (true) {
      val result = sysWait.waitpid(pid, status, sysWait.WNOHANG)
      if (result == pid) {
        val code = if (sysWait.WIFEXITED(!status)) sysWait.WEXITSTATUS(!status) else 128 + sysWait.WTERMSIG(!status)
        return if (configurationRejected && code == 0) 1 else code
      } else if (result < 0 && errno.errno != 4 /* EINTR */ ) {
        return 1
      }
      if (!configurationChecked && signalsReceived == 0 && System.nanoTime() >= nextCheckAt) {
        nextCheckAt = System.nanoTime() + 100L * 1000 * 1000
        control.listeners() match {
          case Some(listeners) =>
            configurationChecked = true
            if (listeners == "{}") {
              configurationRejected = true
              System.err.println(
                "snunit: FreeUnit rejected the configuration, see its log above. Configuration:\n" +
                  config.toJson(selfExecutable())
              )
              signalsReceived = 2 // stop without draining
            }
          case None =>
            if (System.nanoTime() - startedAt > 10L * 1000 * 1000 * 1000) configurationChecked = true
        }
      }
      val signals = signalsReceived
      if (signals > 0) {
        val now = System.nanoTime()
        if (drainStep == 0) {
          drainStartedAt = now
          drainStep = if (control.removeListeners()) 1 else 2
          if (drainStep == 2) signal.kill(pid, signal.SIGQUIT)
        }
        val timedOut = now - drainStartedAt >= timeoutNanos
        if (drainStep == 1 && now >= nextPollAt) {
          nextPollAt = now + 50L * 1000 * 1000
          // If the server can't be asked, don't wait.
          if (signals > 1 || timedOut || control.activeConnections().forall(_ == 0)) {
            drainStep = 2
            signal.kill(pid, signal.SIGQUIT)
          }
        }
        if (terminatedAt == 0L && (signals > 1 || timedOut)) {
          signal.kill(pid, signal.SIGTERM)
          terminatedAt = now
        }
        if (!killed && terminatedAt != 0L && (signals > 2 || now - terminatedAt >= killTimeoutNanos)) {
          signal.kill(pid, signal.SIGKILL)
          killed = true
        }
      }
      Thread.sleep(20)
    }
    0
  }

  /** Certificates are read by `unitd` from the `certs` directory of its state directory. */
  private def writeCertificates(statedir: Path, config: UnitConfig): Unit =
    if (config.certificates.nonEmpty) {
      val certs = Files.createDirectory(statedir.resolve("certs"))
      config.certificates.foreach { (name, pem) =>
        val file = Files.createFile(
          certs.resolve(name),
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
        )
        Files.write(file, pem.getBytes("UTF-8"))
      }
    }

  /** Starts `path` with `args`. Returns its pid.
    *
    * @param stdin
    *   file descriptor to use as the standard input of the process
    * @param newProcessGroup
    *   run the process in a new process group
    */
  private def spawnProcess(path: String, args: Seq[String], stdin: Int = -1, newProcessGroup: Boolean = false): Int =
    Zone {
      def cStrings(values: Seq[String]): Ptr[CString] = {
        val array = alloc[CString](values.length + 1)
        values.zipWithIndex.foreach((value, i) => array(i) = toCString(value))
        array(values.length) = null
        array
      }
      val argv = cStrings(path +: args)
      val envp = cStrings(sys.env.map((k, v) => s"$k=$v").toSeq)

      val actions = alloc[spawn.posix_spawn_file_actions_t]()
      spawn.posix_spawn_file_actions_init(actions)
      if (stdin >= 0) spawn.posix_spawn_file_actions_adddup2(actions, stdin, 0)

      val attributes = alloc[spawn.posix_spawnattr_t]()
      spawn.posix_spawnattr_init(attributes)
      if (newProcessGroup) {
        spawn.posix_spawnattr_setflags(attributes, spawn.POSIX_SPAWN_SETPGROUP.toShort)
        spawn.posix_spawnattr_setpgroup(attributes, 0)
      }

      val pid = alloc[spawn.pid_t]()
      val result = spawn.posix_spawn(pid, toCString(path), actions, attributes, argv, envp)
      spawn.posix_spawn_file_actions_destroy(actions)
      spawn.posix_spawnattr_destroy(attributes)
      if (result != 0) throw new java.io.IOException(s"Cannot run $path: ${fromCString(string.strerror(result))}")
      !pid
    }

  /** A shell process that stops `unitd` and removes the temporary directory when the launcher dies without cleaning up.
    *
    * It reads its standard input, a pipe whose write end is only held by the launcher, so that it sees the end of the
    * input when the launcher exits for any reason, including SIGKILL. The launcher first sends the pid of `unitd`.
    * Before exiting normally it sends `done`.
    */
  private final class Watchdog(pid: Int, fd: Int) {
    def watch(unitdPid: Int): Unit = write(s"$unitdPid\n")

    def stop(): Unit = {
      write("done\n")
      unistd.close(fd)
      // The watchdog exits as soon as it reads `done`.
      val status = stackalloc[CInt]()
      var attempts = 0
      while (sysWait.waitpid(pid, status, sysWait.WNOHANG) == 0 && attempts < 100) {
        Thread.sleep(10)
        attempts += 1
      }
    }

    private def write(text: String): Unit = {
      val bytes = text.getBytes("UTF-8")
      unistd.write(fd, bytes.atUnsafe(0), bytes.length.toCSize)
    }
  }

  private object Watchdog {
    private val script =
      """dir=$1; timeout=$2
        |wait_exit() { n=$1; while [ "$n" -gt 0 ] && kill -0 "$pid" 2>/dev/null; do sleep 1; n=$((n-1)); done; }
        |read -r pid || { rm -rf "$dir"; exit 0; }
        |if read -r msg && [ "$msg" = done ]; then exit 0; fi
        |kill -QUIT "$pid" 2>/dev/null; wait_exit "$timeout"
        |kill -TERM "$pid" 2>/dev/null; wait_exit 5
        |kill -KILL "$pid" 2>/dev/null
        |rm -rf "$dir"
        |""".stripMargin

    /** `None` if the watchdog can't be started: the launcher still works, just without the protection. */
    def start(dir: Path, config: UnitConfig): Option[Watchdog] = {
      val fds = stackalloc[CInt](2)
      if (unistd.pipe(fds) != 0) None
      else {
        val (readFd, writeFd) = (fds(0), fds(1))
        // The write end must not be inherited by the watchdog and unitd, or the end of input would never be seen.
        fcntl.fcntl(writeFd, fcntl.F_SETFD, fcntl.FD_CLOEXEC)
        fcntl.fcntl(readFd, fcntl.F_SETFD, fcntl.FD_CLOEXEC)
        try {
          val pid = spawnProcess(
            "/bin/sh",
            Seq("-c", script, "sh", dir.toString, config.shutdownTimeout.toSeconds.toString),
            stdin = readFd,
            newProcessGroup = true
          )
          Some(new Watchdog(pid, writeFd))
        } catch {
          case _: java.io.IOException =>
            unistd.close(writeFd)
            None
        } finally unistd.close(readFd)
      }
    }
  }

  private def deleteRecursively(path: Path): Unit =
    try {
      if (Files.isDirectory(path)) Files.list(path).forEach(deleteRecursively(_))
      Files.deleteIfExists(path)
    } catch { case _: Exception => () }
}
