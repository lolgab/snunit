package snunit.sbt

import sbt._
import sbt.Keys._
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport._
import SNUnitPlugin.autoImport._

private[sbt] object SNUnitCompat {
  def bundleSettings: Seq[Setting[_]] = Seq(
    snunitBundle := {
      val linked = (Compile / nativeLink).value
      SNUnitPlugin.bundle(linked, target.value / "snunit" / name.value, snunitPlatform.value)
    }
  )
}
