import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.chaining._
import scala.util.control.NonFatal
import scala.util.Try
object TestUtils {
  private def runMillCommand(command: String) = os
    .proc(
      "./mill",
      "-i",
      "--disable-ticker",
      "show",
      command
    )
    .call()

  def withDeployedExample(projectName: String)(f: => Unit) = {
    runMillCommand(s"integration.tests.$projectName.deployTestApp")
    f
  }

  def withDeployedExampleMultiplatform(projectName: String)(f: => Unit) = {
    runMillCommand(s"integration.tests.$projectName.native.deployTestApp")
    val result = runMillCommand(s"integration.tests.$projectName.jvm.launcher").out.lines().head
    val s""""ref:$_:$path"""" = result
    val process = os.proc(path).spawn()
    try { f }
    finally { process.close() }
  }

  val baseUrl = "http://localhost:8081"
}
