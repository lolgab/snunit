package snunit.plugin

import sbt._
import Keys._
import sbt.util.CacheImplicits._
import scala.scalanative.sbtplugin.ScalaNativePlugin
import sjsonnew.{:*:, LList, LNil}

private[plugin] object SjsonnewImplicits {
  implicit val ScriptIso = LList.iso(
    { (s: ClangScripts.Script) => ("path", s.path) :*: ("content", s.content) :*: LNil },
    { in: String :*: String :*: LNil => ClangScripts.Script(in._1, in._2) }
  )

  implicit val ScriptsIso = LList.iso(
    { s: ClangScripts.Scripts => ("clang", s.clang) :*: ("clangpp", s.clangpp) :*: LNil },
    { in: ClangScripts.Script :*: ClangScripts.Script :*: LNil => ClangScripts.Scripts(in._1, in._2) }
  )
}

object DockerClangPlugin extends AutoPlugin {
  import SjsonnewImplicits._

  override def trigger = allRequirements
  override def requires = ScalaNativePlugin

  object autoImport {
    val createDockerClangScripts = taskKey[ClangScripts.Scripts]("create docker clang scripts")
  }

  import autoImport._

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    createDockerClangScripts := {
      val dest = target.value
      val cached = Cache.cached[Unit, ClangScripts.Scripts](dest)((_: Unit) =>
        ClangScripts.createAndWriteClangScripts(dest = dest.toString(), pwd = ".")
      )
      cached.apply(())
    },
    ScalaNativePlugin.autoImport.nativeClang := {
      val scripts = createDockerClangScripts.value
      new java.io.File(scripts.clang.path)
    },
    ScalaNativePlugin.autoImport.nativeClangPP := {
      val scripts = createDockerClangScripts.value
      new java.io.File(scripts.clangpp.path)
    }
  )
}
