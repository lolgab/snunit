package snunit.plugin

import sbt._
import Keys._
import sbt.util.CacheImplicits._
import scala.scalanative.sbtplugin.ScalaNativePlugin
import sjsonnew.{:*:, LList, LNil}

object SNUnitPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = ScalaNativePlugin

  object autoImport {
    private[SNUnitPlugin] val shared = taskKey[SNUnitPluginShared]("")

    val snunitPort = settingKey[Int]("Port when app runs")

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
      new SNUnitPluginShared(logger)
    },
    snunitPort := 8080,
    deployToNGINXUnit := {
      val port = snunitPort.value
      val executable = (Compile / nativeLink).value
      autoImport.shared.value.deployToNGINXUnit(executable.toString, port)
    }
  )
}
