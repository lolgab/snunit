import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.chaining._
import scala.util.control.NonFatal
import scala.util.Try
object TestUtils {
  private def runMillCommand(command: String) = os
    .proc(
      "./mill",
      // adding `-i` breaks the ability to close unitd processes
      "--disable-ticker",
      "show",
      command
    )
    .call()

  def withDeployedExample(projectName: String, crossSuffix: String = "")(f: => Unit) = {
    runMillCommand(s"integration.tests.$projectName$crossSuffix.deployTestApp")
    f
  }
  def withDeployedExampleHttp4s(projectName: String)(f: => Unit) = {
    BuildInfo.http4sVersions.split(':').foreach { versions =>
      withDeployedExample(projectName, s"[$versions]")(f)
    }
  }
  def withDeployedExampleMultiplatform(projectName: String, crossSuffix: String = "")(f: => Unit) = {
    val projectPrefix = s"integration.tests.$projectName$crossSuffix"
    runMillCommand(s"$projectPrefix.native.deployTestApp")
    val result = runMillCommand(s"$projectPrefix.jvm.launcher").out.lines().head
    val s""""ref:$_:$path"""" = result
    val process = os.proc(path).spawn()
    Thread.sleep(1000)
    try { f }
    finally { process.close() }
  }
  def withDeployedExampleMultiplatformCross(projectName: String)(f: => Unit) = {
    BuildInfo.scalaVersions.split(':').foreach { scalaVersion =>
      withDeployedExampleMultiplatform(projectName, s"[$scalaVersion]")(f)
    }
  }

  def runOnAllPlatforms(f: String => Unit) = {
    Seq("http://localhost:8080", baseUrl).foreach(f)
  }

  val baseUrl = s"http://localhost:${BuildInfo.port}"
}
