package snunit.plugin

import mill._
import mill.scalanativelib._

trait SNUnit extends ScalaNativeModule {
  def snunitVersion: String = snunit.plugin.internal.BuildInfo.snunitVersion

  def snunitCurlCommand: Target[Seq[String]] = T { Seq("curl") }

  private def shared = T.worker {
    val log = T.log
    val logger = new snunit.plugin.Logger {
      def info(s: String): Unit = log.info(s)
      def warn(s: String): Unit = log.error(s)
      def error(s: String): Unit = log.error(s)
    }
    new SNUnitPluginShared(BuildTool.Mill, logger, snunitCurlCommand())
  }

  /** Port where SNUnit app runs
    */
  def snunitPort: Target[Int] = T { 8080 }

  /** Deploy app to NGINX Unit
    */
  def deployToNGINXUnit(): Command[Unit] = T.command {
    val executable = nativeLink()
    val port = snunitPort()
    shared().deployToNGINXUnit(executable.toString, port)
  }

}
