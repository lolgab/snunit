package snunit.plugin

import sbt._
import Keys._
import sbt.util.CacheImplicits._
import scala.scalanative.sbtplugin.ScalaNativePlugin
import sjsonnew.{:*:, LList, LNil}

object SNUnitPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = ScalaNativePlugin

  private val shared = taskKey[SNUnitPluginShared]("")
  object autoImport {
    val snunitPort = settingKey[Int]("Port where the SNUnit app runs")
    val snunitCurlCommand = settingKey[Seq[String]]("curl command to use")
    val snunitAppName = settingKey[String]("app name in NGINX Unit config")
    val snunitConfig = taskKey[String]("NGINX Unit configuration")
    val snunitVersion: String = snunit.plugin.internal.BuildInfo.snunitVersion

    val deployToNGINXUnit = taskKey[Unit]("Deploy app to NGINX Unit")
  }

  import autoImport._
  import ScalaNativePlugin.autoImport._

  override def projectSettings = super.projectSettings ++ Seq(
    shared := {
      val str = streams.value
      val logger = new Logger {
        def info(s: String): Unit = str.log.info(s)
        def warn(s: String): Unit = str.log.warn(s)
        def error(s: String): Unit = str.log.error(s)
      }
      new SNUnitPluginShared(BuildTool.Sbt, logger, snunitCurlCommand.value)
    },
    snunitCurlCommand := Seq("curl"),
    snunitPort := 8080,
    snunitAppName := name.value,
    snunitConfig := {
      val appName = snunitAppName.value
      val port = snunitPort.value
      SNUnitPluginShared.defaultConfig(appName, port)
    },
    deployToNGINXUnit := {
      val config = snunitConfig.value
      val executable = (Compile / nativeLink).value
      val appName = snunitAppName.value
      shared.value.deployToNGINXUnit(appName = appName, config = config, executable = executable.toString)
    }
  )
}
