package snunit.plugin

import mill._
import mill.scalanativelib._
import upickle.default._

trait SNUnit extends ScalaNativeModule {
  def snunitVersion: String = snunit.plugin.internal.BuildInfo.snunitVersion
  def snunitNGINXUnitVersion: Target[String] = Task { "1.34.1" }
  def snunitNGINXUnitUser: Target[Option[String]] = Task.Input { T.env.get("USER") }
  def snunitNGINXUnitGroup: Target[Option[String]] = Task.Input { T.env.get("GROUP") }
  def snunitNGINXUnitSources = Task {
    val dir = s"unit-${snunitNGINXUnitVersion()}"
    val file = s"$dir.tar.gz"
    os.write(Task.dest / file, requests.get.stream(s"https://sources.nginx.org/unit/$file"))
    val unitDir = Task.dest / dir
    os.proc("tar", "xzf", Task.dest / file).call(cwd = Task.dest)
    PathRef(Task.dest / dir)
  }

  def snunitNGINXUnitBinary = Task {
    val platform = System.getProperty("os.name", "unknown").toLowerCase()

    val openSslParams =
      if (platform.contains("mac")) {
        List("--cc-opt=-I/opt/homebrew/opt/openssl@3/include", "--ld-opt=-L/opt/homebrew/opt/openssl@3/lib")
      } else Nil

    val unitDir = snunitNGINXUnitSources().path

    os.proc(
      "./configure",
      "--logdir=./logdir",
      "--log=/dev/stdout",
      snunitNGINXUnitUser().map(user => s"--user=$user"),
      snunitNGINXUnitGroup().map(group => s"--group=$group"),
      "--runstatedir=./runstatedir",
      "--pid=unit.pid",
      "--control=unix:control.sock",
      "--modulesdir=./modulesdir",
      "--statedir=./statedir",
      "--tmpdir=/tmp",
      // "--otel", TODO: Support otel
      "--openssl",
      openSslParams
    ).call(cwd = unitDir, stdout = os.Inherit)
    os.proc("make", "build/sbin/unitd", "build/lib/libunit.a").call(cwd = unitDir, stdout = os.Inherit)
    val unitd = Task.dest / "unitd"
    val libunit = Task.dest / "libunit.a"
    os.copy(unitDir / "build/sbin/unitd", unitd)
    os.copy(unitDir / "build/lib/libunit.a", libunit)
    SNUnit.NGINXUnitInstallation(unitd = PathRef(unitd), libunit = PathRef(libunit))
  }

  /** Port where SNUnit app runs
    */
  def snunitPort: Target[Int] = Task { 8080 }

  def snunitNGINXUnitConfig: Target[String] =
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

  def snunitNGINXUnitWorkdir = Task {
    Task.dest
  }

  private def runImpl = Task.Anon {
    val wd = snunitNGINXUnitWorkdir()
    snunitKillNGINXUnit().apply()
    val statedir = wd / "statedir"
    os.makeDir.all(statedir)
    val nginxUnit = snunitNGINXUnitBinary().unitd.path
    val nginxUnitConfig = snunitNGINXUnitConfig()
    os.write.over(statedir / "conf.json", nginxUnitConfig)
    (wd, nginxUnit)
  }

  /** Run app on NGINX Unit
    */
  override def run(args: Task[Args] = Task.Anon(Args())): Command[Unit] = Task.Command {
    val (wd, nginxUnit) = runImpl()
    os.proc(nginxUnit, "--no-daemon").call(wd, stdout = os.Inherit)

    ()
  }

  override def runBackground(args: String*): Command[Unit] = Task.Command {
    val (procUuidPath, procLockfile, procUuid) = _root_.mill.snunitinternal.RunModule.backgroundSetup(Task.dest)

    val (wd, nginxUnit) = runImpl()

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
        nginxUnit.toString,
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

  def snunitKillNGINXUnit(): Command[Unit] = Task.Command {
    val pidFile = snunitNGINXUnitWorkdir() / "unit.pid"

    if (os.exists(pidFile)) {
      os.proc("kill", os.read(snunitNGINXUnitWorkdir() / "unit.pid").trim).call(stdout = os.Inherit)
    }

    ()
  }

  // def buildDocker(): Command[Unit] = T.command {
  //   // TODO
  // }

  override def nativeLinkingOptions: Target[Seq[String]] = Task {
    val unitBinary = snunitNGINXUnitBinary()
    super.nativeLinkingOptions() ++ Seq(
      unitBinary.libunit.path.toString
    )
  }
}

object SNUnit {
  case class NGINXUnitInstallation(unitd: mill.api.PathRef, libunit: mill.api.PathRef)
  object NGINXUnitInstallation {
    implicit val rw: ReadWriter[NGINXUnitInstallation] = macroRW
  }

  // TODO: Support programmatic config
  case class NGINXUnitConfig(
      listeners: NGINXUnitConfig.Listeners,
      applications: NGINXUnitConfig.Applications
  )
  object NGINXUnitConfig {
    case class Listeners()
    object Listeners {
      implicit val rw: ReadWriter[Listeners] = macroRW
    }
    case class Applications()
    object Applications {
      implicit val rw: ReadWriter[Applications] = macroRW
    }
    implicit val rw: ReadWriter[NGINXUnitConfig] = macroRW
  }
}
