import mill._, mill.scalalib._, mill.scalanativelib._, mill.scalanativelib.api._
import $ivy.`com.lihaoyi::mill-contrib-bsp:$MILL_VERSION`
import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`

trait Common extends ScalaNativeModule {
  def organization = "com.github.lolgab"
  def name = "snunit"

  def scalaVersion = "2.11.12"
  def scalaNativeVersion = "0.4.0-M2"

  def nativeLinkingOptions =
    T {
      Array(
        "/usr/local/lib/libunit.a"
      ) ++ super.nativeLinkingOptions()
    }

  def baseTestConfig(binary: os.Path, numProcesses: Int = 1, appName: String = "test_app", port: Int = 8081) =
    ujson.Obj(
      "applications" -> ujson.Obj(
        appName -> ujson.Obj(
          "type" -> "external",
          "working_directory" -> (binary / os.up).toString,
          "executable" -> binary.last,
          "processes" -> numProcesses
        )
      ),
      "listeners" -> ujson.Obj(
        s"*:$port" -> ujson.Obj(
          "pass" -> s"applications/$appName"
        )
      )
    )

  def deployTestApp() = {
    def doCurl(data: String) = {
      import scala.sys.process._
      assert(
        Seq(
          "curl",
          "-X",
          "PUT",
          "--data-binary",
          data,
          "--unix-socket",
          "/usr/local/var/run/unit/control.sock",
          "http://localhost/config"
        ).! == 0
      )
    }

    T.command {
      val binary = nativeLink()
      doCurl(baseTestConfig(binary = binary, numProcesses = 0).toString)
      doCurl(baseTestConfig(binary = binary, numProcesses = 1).toString)
    }
  }

  object test extends Tests {
    def testFrameworks = Seq.empty[String]

    def nativeLinkStubs = true
  }
}

object snunit extends Common

trait UsesCore extends ScalaModule {
  def moduleDeps: Seq[ScalaModule] = Seq(snunit)
}

object `snunit-scala-native-loop` extends Common with UsesCore {
  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"dev.whaling::native-loop-core::0.1.1-SNAPSHOT"
      )
    }
}

object `snunit-autowire` extends Common with UsesCore {
  def moduleDeps: Seq[ScalaModule] = Seq(`snunit-scala-native-loop`)
  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"com.lihaoyi::autowire::0.2.7",
        ivy"com.lihaoyi::upickle::1.1.0"
      )
    }
}

object examples extends Module {
  object `low-level` extends Common with UsesCore
  object `hello-world` extends Common with UsesCore
  object autowire extends Common with UsesCore {
    def moduleDeps = super.moduleDeps :+ `snunit-autowire`
  }
  object `async` extends Common with UsesCore {
    def moduleDeps = super.moduleDeps :+ `snunit-scala-native-loop`
  }
}
