package snunit.plugin

import mill._
import mill.scalanativelib._
import upickle.default._

trait SNUnit extends ScalaNativeModule {
  def snunitVersion: String = snunit.plugin.internal.BuildInfo.snunitVersion
  def snunitFreeUnitVersion: Target[String] = Task { "1.35.5" }
  def snunitFreeUnitUser: Target[Option[String]] = Task.Input { T.env.get("USER") }
  def snunitFreeUnitGroup: Target[Option[String]] = Task.Input { T.env.get("GROUP") }
  def snunitFreeUnitSources = Task {
    val version = snunitFreeUnitVersion()
    val file = s"freeunit-$version.tar.gz"
    val url = s"https://github.com/freeunitorg/freeunit/archive/refs/tags/$version.tar.gz"
    os.write(Task.dest / file, requests.get.stream(url))
    os.proc("tar", "xzf", Task.dest / file, "--strip-components=1").call(cwd = Task.dest)
    PathRef(Task.dest)
  }

  def snunitFreeUnitBinary = Task {
    val platform = System.getProperty("os.name", "unknown").toLowerCase()

    val openSslParams =
      if (platform.contains("mac")) {
        List("--cc-opt=-I/opt/homebrew/opt/openssl@3/include", "--ld-opt=-L/opt/homebrew/opt/openssl@3/lib")
      } else Nil

    val unitDir = snunitFreeUnitSources().path

    os.proc(
      "./configure",
      "--logdir=./logdir",
      "--log=/dev/stdout",
      snunitFreeUnitUser().map(user => s"--user=$user"),
      snunitFreeUnitGroup().map(group => s"--group=$group"),
      "--runstatedir=./runstatedir",
      "--pid=unit.pid",
      "--control=unix:control.sock",
      "--modulesdir=./modulesdir",
      "--statedir=./statedir",
      "--tmpdir=/tmp",
      "--otel",
      "--openssl",
      openSslParams
    ).call(cwd = unitDir, stdout = os.Inherit)
    os.proc("make", "build/sbin/unitd", "build/lib/libunit.a").call(cwd = unitDir, stdout = os.Inherit)
    val unitd = Task.dest / "unitd"
    val libunit = Task.dest / "libunit.a"
    os.copy(unitDir / "build/sbin/unitd", unitd)
    os.copy(unitDir / "build/lib/libunit.a", libunit)
    SNUnit.FreeUnitInstallation(unitd = PathRef(unitd), libunit = PathRef(libunit))
  }

  /** Port where SNUnit app runs
    */
  def snunitPort: Target[Int] = Task { 8080 }

  def snunitFreeUnitConfig: Target[String] =
    s"""{
       |  "listeners": {
       |    "*:${snunitPort()}": {
       |      "pass": "applications/app"
       |    }
       |  },
       |  "applications": {
       |    "app": {
       |      "type": "external",
       |      "executable": "${nativeLink()}"
       |    }
       |  }
       |}""".stripMargin

  def snunitFreeUnitWorkdir = Task {
    Task.dest
  }

  private def runImpl = Task.Anon {
    val wd = snunitFreeUnitWorkdir()
    snunitKillFreeUnit().apply()
    val statedir = wd / "statedir"
    os.makeDir.all(statedir)
    val freeUnit = snunitFreeUnitBinary().unitd.path
    val freeUnitConfig = snunitFreeUnitConfig()
    os.write.over(statedir / "conf.json", freeUnitConfig)
    (wd, freeUnit)
  }

  /** Run app on FreeUnit
    */
  override def run(args: Task[Args] = Task.Anon(Args())): Command[Unit] = Task.Command {
    val (wd, freeUnit) = runImpl()
    os.proc(freeUnit, "--no-daemon").call(wd, stdout = os.Inherit)

    ()
  }

  override def runBackground(args: String*): Command[Unit] = Task.Command {
    val (procUuidPath, procLockfile, procUuid) = _root_.mill.snunitinternal.RunModule.backgroundSetup(Task.dest)

    val (wd, freeUnit) = runImpl()

    mill.util.Jvm.runSubprocess(
      mainClass = "mill.scalalib.backgroundwrapper.MillBackgroundWrapper",
      classPath = mill.scalalib.ZincWorkerModule.backgroundWrapperClasspath().map(_.path).toSeq,
      jvmArgs = Nil,
      envArgs = forkEnv(),
      mainArgs = Seq(
        procUuidPath.toString,
        procLockfile.toString,
        procUuid,
        "500",
        "<subprocess>",
        freeUnit.toString,
        "--no-daemon"
      ) ++ args,
      workingDir = wd,
      background = true,
      useCpPassingJar = false,
      runBackgroundLogToConsole = true,
      javaHome = mill.scalalib.ZincWorkerModule.javaHome().map(_.path)
    )

    ()
  }

  def snunitKillFreeUnit(): Command[Unit] = Task.Command {
    val pidFile = snunitFreeUnitWorkdir() / "unit.pid"

    if (os.exists(pidFile)) {
      os.proc("kill", os.read(snunitFreeUnitWorkdir() / "unit.pid").trim).call(stdout = os.Inherit)
    }

    ()
  }

  // def buildDocker(): Command[Unit] = T.command {
  //   // TODO
  // }

  override def nativeLinkingOptions: Target[Seq[String]] = Task {
    val unitBinary = snunitFreeUnitBinary()
    super.nativeLinkingOptions() ++ Seq(
      unitBinary.libunit.path.toString
    )
  }

  @deprecated("Use snunitFreeUnitVersion", "SNUnit next release")
  def snunitNGINXUnitVersion: Target[String] = snunitFreeUnitVersion
  @deprecated("Use snunitFreeUnitUser", "SNUnit next release")
  def snunitNGINXUnitUser: Target[Option[String]] = snunitFreeUnitUser
  @deprecated("Use snunitFreeUnitGroup", "SNUnit next release")
  def snunitNGINXUnitGroup: Target[Option[String]] = snunitFreeUnitGroup
  @deprecated("Use snunitFreeUnitSources", "SNUnit next release")
  def snunitNGINXUnitSources = snunitFreeUnitSources
  @deprecated("Use snunitFreeUnitBinary", "SNUnit next release")
  def snunitNGINXUnitBinary = snunitFreeUnitBinary
  @deprecated("Use snunitFreeUnitConfig", "SNUnit next release")
  def snunitNGINXUnitConfig: Target[String] = snunitFreeUnitConfig
  @deprecated("Use snunitFreeUnitWorkdir", "SNUnit next release")
  def snunitNGINXUnitWorkdir = snunitFreeUnitWorkdir
  @deprecated("Use snunitKillFreeUnit", "SNUnit next release")
  def snunitKillNGINXUnit(): Command[Unit] = snunitKillFreeUnit()
}

object SNUnit {
  case class FreeUnitInstallation(unitd: mill.api.PathRef, libunit: mill.api.PathRef)
  object FreeUnitInstallation {
    implicit val rw: ReadWriter[FreeUnitInstallation] = macroRW
  }

  @deprecated("Use FreeUnitInstallation", "SNUnit next release")
  type NGINXUnitInstallation = FreeUnitInstallation
  @deprecated("Use FreeUnitInstallation", "SNUnit next release")
  val NGINXUnitInstallation = FreeUnitInstallation

  // TODO: Support programmatic config
  case class FreeUnitConfig(
      listeners: FreeUnitConfig.Listeners,
      applications: FreeUnitConfig.Applications
  )
  object FreeUnitConfig {
    case class Listeners()
    object Listeners {
      implicit val rw: ReadWriter[Listeners] = macroRW
    }
    case class Applications()
    object Applications {
      implicit val rw: ReadWriter[Applications] = macroRW
    }
    implicit val rw: ReadWriter[FreeUnitConfig] = macroRW
  }

  @deprecated("Use FreeUnitConfig", "SNUnit next release")
  type NGINXUnitConfig = FreeUnitConfig
  @deprecated("Use FreeUnitConfig", "SNUnit next release")
  val NGINXUnitConfig = FreeUnitConfig
}
