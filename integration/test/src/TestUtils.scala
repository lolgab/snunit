import scala.util.chaining._
import scala.util.control.NonFatal
object TestUtils {
  def withDeployedExample(projectName: String)(f: => Unit) = {
    os.proc(
      "./mill",
      "-j",
      sys.runtime.availableProcessors,
      s"examples.$projectName.deployTestApp"
    ).call()
    f
  }

  val baseUrl = "http://localhost:8081"
}
