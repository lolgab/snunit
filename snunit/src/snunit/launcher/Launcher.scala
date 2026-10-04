package snunit.launcher

import java.nio.file.{Files, Path, Paths}
import scala.scalanative.libc.stdlib.getenv
import scala.scalanative.meta.LinktimeInfo
import scala.scalanative.posix.signal
import scala.scalanative.posix.unistd
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

/** Makes an SNUnit executable run standalone.
  *
  * When the executable is started directly (not by `unitd`) and contains an embedded `unitd` (see [[EmbeddedUnitd]]),
  * it starts `unitd` with a generated configuration that runs the same executable as the application, then exits when
  * `unitd` exits. When it is started by `unitd` this does nothing.
  *
  * Configuration is read from environment variables:
  *   - `SNUNIT_PORT`: port to listen on (default `8080`)
  *   - `SNUNIT_PROCESSES`: number of application processes (default: `unitd` default, 1)
  */
private[snunit] object Launcher {
  private val UnitInitEnv = c"NXT_UNIT_INIT"

  @extern private object darwin {
    def _NSGetExecutablePath(buf: CString, bufsize: Ptr[CUnsignedInt]): CInt = extern
  }

  @volatile private var unitdPid: Int = 0

  private val forwardSignal = CFuncPtr1.fromScalaFunction[CInt, Unit] { sig =>
    val pid = unitdPid
    if (pid > 0) signal.kill(pid, sig)
  }

  def runIfNeeded(): Unit =
    if (getenv(UnitInitEnv) == null) {
      EmbeddedUnitd.bytes() match {
        case Some(bytes) => run(bytes)
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

  private def run(unitdBytes: Array[Byte]): Nothing = {
    val port = sys.env.getOrElse("SNUNIT_PORT", "8080").toInt
    val processes = sys.env.get("SNUNIT_PROCESSES").map(_.toInt)

    // Short path: unix socket paths are limited to ~100 characters.
    val dir = Files.createTempDirectory(Paths.get("/tmp"), "snunit")
    val statedir = Files.createDirectory(dir.resolve("statedir"))
    Files.createDirectory(dir.resolve("tmp"))
    Files.write(statedir.resolve("conf.json"), config(port, processes, selfExecutable()).getBytes("UTF-8"))

    val unitd = EmbeddedUnitd.executablePath(unitdBytes)
    val command = new java.util.ArrayList[String]()
    Seq(
      unitd,
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
    ).foreach(command.add)

    val process = new ProcessBuilder(command).inheritIO().start()
    unitdPid = process.pid().toInt
    signal.signal(signal.SIGTERM, forwardSignal)
    signal.signal(signal.SIGINT, forwardSignal)

    val code = process.waitFor()
    deleteRecursively(dir)
    sys.exit(code)
  }

  private def config(port: Int, processes: Option[Int], executable: String): String = {
    val processesJson = processes.fold("")(n => s""", "processes": $n""")
    s"""{
       |  "listeners": { "*:$port": { "pass": "applications/app" } },
       |  "applications": {
       |    "app": { "type": "external", "executable": ${jsonString(executable)}$processesJson }
       |  }
       |}""".stripMargin
  }

  private def jsonString(s: String): String =
    "\"" + s.flatMap {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case c    => c.toString
    } + "\""

  private def deleteRecursively(path: Path): Unit =
    try {
      if (Files.isDirectory(path)) Files.list(path).forEach(deleteRecursively(_))
      Files.deleteIfExists(path)
    } catch { case _: Exception => () }
}
