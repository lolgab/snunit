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
    },
    snunitBundleReleaseFast := {
      val linked = (Compile / nativeLinkReleaseFast).value
      SNUnitPlugin.bundle(linked, target.value / "snunit" / "release-fast" / name.value, snunitPlatform.value)
    },
    snunitBundleReleaseFull := {
      val linked = (Compile / nativeLinkReleaseFull).value
      SNUnitPlugin.bundle(linked, target.value / "snunit" / "release-full" / name.value, snunitPlatform.value)
    }
  )
}
