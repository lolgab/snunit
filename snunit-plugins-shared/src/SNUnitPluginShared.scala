package snunit.plugin

import scala.sys.process._
import java.io._
import scala.util.control.NonFatal

private[plugin] class SNUnitPluginShared(buildTool: BuildTool, logger: Logger, snunitCurlCommand: Seq[String]) {
  private def unitControlSocket(): File = {
    val stringPath = Seq("unitd", "--help").!!.linesIterator
      .find(_.contains("unix:"))
      .get
      .replaceAll(".+unix:", "")
      .stripSuffix("\"")
    new File(stringPath)
  }
  def isUnitInstalled(): Boolean = {
    try {
      // collect stderr from unitd
      val err = new StringBuilder
      val processLogger = ProcessLogger(_ => (), err ++= _)

      Seq("unitd", "--version").!!(processLogger)
      err.containsSlice("unit version:")
    } catch {
      case NonFatal(e) =>
        logger.error("""Can't find unitd in the path.
          |Please install NGINX Unit first.
          |You can find instructions here: https://unit.nginx.org/installation""".stripMargin)
        false
    }
  }
  private final val notRunningErrorMessage = """NGINX Unit is not running.
    |You can run it in a separate terminal with:
    |unitd --log /dev/stdout --no-daemon
    |
    |You can also run it as a daemon:
    |
    |If you use brew:
    |brew services start unit
    |
    |If you use systemd:
    |sudo systemctl start unit.service
    |
    |You can find more instructions here: https://unit.nginx.org/installation""".stripMargin

  private def controlSocketExists(control: File): Boolean = {
    val exists = control.exists()
    if (!exists) {
      logger.error(s"""Control socket doesn't exist in the default path (${control.toString}).
           |This means that $notRunningErrorMessage""".stripMargin)
    }
    exists
  }
  def isUnitRunning(control: File): Boolean = {
    try {
      doCurl(control, "http://localhost/config")
      true
    } catch {
      case NonFatal(e) =>
        if (control.canRead() && control.canWrite()) {
          val setting = buildTool match {
            case BuildTool.Sbt  => """snunitCurlCommand := Seq("sudo", "curl")"""
            case BuildTool.Mill => """override def snunitCurlCommand = Seq("sudo", "curl")"""
          }
          logger.error(s"""You don't seem to have permissions to read/write the control socket.
            |For security reasons NGINX Unit doesn't allow non-root users to change Unit's configuration.
            |You can perform Unit commands by changing the curl command used by snunit to connect to Unit:
            |  $setting
            |""".stripMargin)
        } else {
          logger.error(notRunningErrorMessage)
        }
        false
    }
  }

  private def doCurl(control: File, command: String*) =
    (snunitCurlCommand ++ Seq("-sL", "--unix-socket", control.toString) ++ command).!!
  def deployToNGINXUnit(appName: String, config: String, executable: String): Unit = {
    require(isUnitInstalled(), "unitd is not installed")
    val control = unitControlSocket()
    require(controlSocketExists(control), "control socket doesn't exist")
    require(isUnitRunning(control), "unitd is not available")
    val configToApply = config.replaceAllLiterally(SNUnitPluginShared.binaryPlaceholder, executable)
    println(configToApply)
    val result = doCurl(
      control,
      "-X",
      "PUT",
      "-d",
      configToApply,
      "http://localhost/config"
    )
    logger.info(result)
    restartApp(control, appName = appName)
  }

  private def restartApp(control: File, appName: String) = {
    val result = doCurl(control, s"http://localhost/control/applications/$appName/restart")

    logger.info(result)
  }
}
private[plugin] object SNUnitPluginShared {
  private final val binaryPlaceholder = "$SNUNIT_BINARY"
  def defaultConfig(appName: String, port: Int): String = {
    s"""{
      "listeners": {
        "*:$port": {
          "pass": "applications/$appName"
        }
      },
      "applications": {
        "$appName": {
          "type": "external",
          "executable": "$binaryPlaceholder"
        }
      }
    }"""
  }
}
