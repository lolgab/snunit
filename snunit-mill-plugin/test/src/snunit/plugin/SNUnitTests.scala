package snunit.plugin

import mill._
import mill.scalalib._
import mill.testkit.{TestBaseModule, UnitTester}
import utest._

object SNUnitTests extends TestSuite {
  def tests: Tests = Tests {
    test("simple") {
      object build extends TestBaseModule with SNUnit {
        def scalaVersion = BuildInfo.scalaVersion
        def scalaNativeVersion = BuildInfo.scalaNativeVersion
        override def ivyDeps = Task { super.ivyDeps() ++ Agg(ivy"com.github.lolgab::snunit::$snunitVersion") }
      }

      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR").split(";").head)

      UnitTester(build, resourceFolder / "simple").scoped { eval =>
        scala.concurrent.ExecutionContext.global.execute(() => eval(build.snunitRunNGINXUnit()))
        var started = false
        while (!started) {
          try {
            val response = requests.get("http://127.0.0.1:8080", check = false).text()
            started = true
            response ==> "TEST SNUnit Mill Plugin"
          } catch {
            case (_: java.net.ConnectException) | _: requests.UnknownHostException =>
              println("waiting for server to start...")
              Thread.sleep(5000)
          }
        }
      }
    }
  }
}
