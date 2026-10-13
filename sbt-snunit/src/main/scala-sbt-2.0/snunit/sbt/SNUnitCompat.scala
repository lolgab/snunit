package snunit.sbt

import sbt._
import sbt.Keys._
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport._
import SNUnitPlugin.autoImport._

private[sbt] object SNUnitCompat {
  def bundleSettings: Seq[Setting[_]] = Seq(
    snunitBundle := Def.uncached {
      val linked = fileConverter.value.toPath((Compile / nativeLink).value).toFile
      SNUnitPlugin.bundle(linked, target.value / "snunit" / name.value, snunitPlatform.value)
    }
  )
}
