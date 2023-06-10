import $exec.plugins
import mill._
import mill.scalalib._
import mill.scalanativelib._
import snunit.plugin._

val port = 8087

object module extends ScalaNativeModule with SNUnit {
  def scalaVersion = "3.3.0"
  def scalaNativeVersion = "0.4.14"
  def snunitPort = port
  def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.github.lolgab::snunit::$snunitVersion"
  )
}
def verify() = T.command {
  module.deployToNGINXUnit()()
  assert(requests.get(s"http://127.0.0.1:$port").text == "Hello world")
}
