package snunit.plugin

import mill._
import mill.define._
import mill.scalanativelib._

trait SNUnit extends ScalaNativeModule {
  private val shared = T.worker {
    val log = T.log
    val logger = new snunit.plugin.Logger {
      def info(s: String): Unit = log.info(s)
      def warn(s: String): Unit = log.error(s)
      def error(s: String): Unit = log.error(s)
    }
    new SNUnitPluginShared(logger)
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
