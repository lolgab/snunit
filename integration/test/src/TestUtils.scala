import scala.util.chaining._
import scala.util.control.NonFatal
object TestUtils {
  def withDeployedExample(projectName: String)(f: => Unit) = {
    os.proc(
      Seq(
        "./mill",
        s"examples.$projectName.deployTestApp"
      )
    ).call()
    f
  }

  val baseUrl = "http://localhost:8081"
}
