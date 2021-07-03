package snunit.plugin

import mill._
import mill.scalanativelib._
import snunit.plugin.ClangScripts
import upickle.default._

private[plugin] object UpickleImplicits {
  implicit val scriptRW: ReadWriter[ClangScripts.Script] = macroRW[ClangScripts.Script]
  implicit val scriptsRW: ReadWriter[ClangScripts.Scripts] = macroRW[ClangScripts.Scripts]
}

/**
  * Uses Clang from a Docker image to allow
  * building SNUnit binaries for Debian Linux on
  * other operative systems
  */
trait DockerClang extends ScalaNativeModule {
  import UpickleImplicits._
  private def createAndWriteClangScripts = T {
    ClangScripts.createAndWriteClangScripts(
      dest = T.dest.toString,
      pwd = os.pwd.toString
    )
  }

  override def nativeClang = T {
    val scripts = createAndWriteClangScripts()
    os.Path(scripts.clang.path)
  }
  override def nativeClangPP = T {
    val scripts = createAndWriteClangScripts()
    os.Path(scripts.clangpp.path)
  }
}
