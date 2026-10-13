package snunit.sbt

import sbt._
import sbt.Keys._
import scala.scalanative.sbtplugin.ScalaNativePlugin
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport._
import _root_.snunit.build.SNUnitBuild
import _root_.snunit.build.SNUnitBuildException

/**
 * Turns a Scala Native project using SNUnit into a single executable embedding `unitd`.
 *
 * Enable it with `enablePlugins(SNUnitPlugin)`. Then `snunitBundle` gives the executable to deploy.
 */
object SNUnitPlugin extends AutoPlugin {
  override def requires: Plugins = ScalaNativePlugin
  override def trigger: PluginTrigger = noTrigger

  object autoImport {
    val snunitPlatform = settingKey[String](
      "Platform to embed unitd for: linux-x86_64, linux-aarch64, macos-x86_64 or macos-aarch64 (default: this machine)"
    )
    val snunitBundle = taskKey[File](
      "Links the application and returns an executable that embeds unitd (a copy of the linked binary on Linux)"
    )
  }

  import autoImport._

  private def failOnBuildError[A](body: => A): A =
    try body
    catch { case e: SNUnitBuildException => sys.error(e.getMessage) }

  /** Embeds unitd in `linked`, writing the result to `output`. Returns the file to deploy. */
  private[sbt] def bundle(linked: File, output: File, platform: String): File =
    // On macOS unitd is embedded while linking (see nativeConfig below), there's nothing left to do.
    if (platform.startsWith("macos")) linked
    else {
      failOnBuildError(SNUnitBuild.bundle(linked.toPath, output.toPath, platform))
      output
    }

  override def projectSettings: Seq[Setting[_]] = Seq(
    snunitPlatform := SNUnitBuild.hostPlatform,
    nativeConfig := {
      val platform = snunitPlatform.value
      val linkOptions = failOnBuildError(SNUnitBuild.linkOptions(platform))
      nativeConfig.value.withLinkingOptions(_ ++ linkOptions)
    }
  ) ++ SNUnitCompat.bundleSettings
}
