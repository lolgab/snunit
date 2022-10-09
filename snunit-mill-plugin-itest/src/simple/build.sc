import $exec.plugins
import mill._
import mill.scalalib._
import mill.scalanativelib._
import snunit.plugin._

val port = 8087

object module extends ScalaNativeModule with SNUnit {
  def scalaVersion = "2.13.8"
  def scalaNativeVersion = "0.4.7"
  def snunitPort = port
  def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.github.lolgab::snunit::0.1.0"
  )
}
def verify() = T.command {
  module.deployToNGINXUnit()()
  requests.get(s"http://127.0.0.1:$port").text
}
