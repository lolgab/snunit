import mill._, mill.scalalib._, mill.scalanativelib._, mill.scalanativelib.api._
import mill.scalalib.publish._
import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
import $ivy.`com.goyeau::mill-scalafix:0.1.5`
import com.goyeau.mill.scalafix.ScalafixModule

val upickle = ivy"com.lihaoyi::upickle::1.2.0"

trait Common extends ScalaNativeModule with ScalafixModule {
  def organization = "com.github.lolgab"
  def name = "snunit"

  def scalaVersion = "2.11.12"
  def scalaNativeVersion = "0.4.0-M2"

  def valueForOs(linux: String, macos: String, envVariable: Option[String] = None): String =
    envVariable
      .flatMap(sys.env.get)
      .getOrElse(
        sys.props("os.name") match {
          case "Linux"    => linux
          case "Mac OS X" => macos
        }
      )

  val libunitPath = valueForOs(
    linux = "/usr/lib/x86_64-linux-gnu/libunit.a",
    macos = "/usr/local/lib/libunit.a"
  )

  val unitSocketPath = valueForOs(
    linux = "/var/run/control.unit.sock",
    macos = "/usr/local/var/run/unit/control.sock",
    envVariable = Some("UNIT_CONTROL_SOCKET_PATH")
  )

  def nativeLinkingOptions = T(libunitPath +: super.nativeLinkingOptions())

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
    def doCurl(json: ujson.Value) = {
      import scala.sys.process._
      assert(
        Seq(
          "curl",
          "-X",
          "PUT",
          "--data-binary",
          json.toString,
          "--unix-socket",
          unitSocketPath,
          "http://localhost/config"
        ).! == 0
      )
    }
    T.command {
      val binary = nativeLink()
      doCurl(baseTestConfig(binary = binary, numProcesses = 0))
      doCurl(baseTestConfig(binary = binary, numProcesses = 8))
    }
  }

  def scalacOptions = Seq("-Ywarn-unused")

  def scalafixIvyDeps = Agg(ivy"com.github.liancheng::organize-imports:0.4.0")

  object test extends Tests {
    def testFrameworks = Seq.empty[String]

    def nativeLinkStubs = true
  }
}

trait Publish extends PublishModule {
  def pomSettings =
    PomSettings(
      description = "Scala Native server using NGINX Unit",
      organization = "com.github.lolgab",
      url = "https://github.com/lolgab/snunit",
      licenses = Seq(License.MIT),
      scm = SCM(
        "git://github.com/lolgab/snunit.git",
        "scm:git://github.com/lolgab/snunit.git"
      ),
      developers = Seq(
        Developer("lolgab", "Lorenzo Gabriele", "https://github.com/lihaoyi")
      )
    )
  def publishVersion = "0.0.1-SNAPSHOT"
}

object snunit extends Common with Publish {
  def ivyDeps = T { super.ivyDeps() ++ Seq(ivy"com.lihaoyi::geny::0.6.2") }
}

object `snunit-async` extends Common with Publish {
  def moduleDeps = Seq(snunit)

  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"com.github.lolgab::native-loop-core::0.1.1"
      )
    }
}

object `snunit-routes` extends Common with Publish {
  def moduleDeps = Seq(snunit)

  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"tech.sparse::trail::0.3.0"
      )
    }
}

object `snunit-autowire` extends Common with Publish {
  def moduleDeps = Seq(`snunit-async`)
  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"com.github.lolgab::autowire::0.3.2",
        upickle
      )
    }
}

object integration extends ScalaModule {
  object tests extends Module {
    object `hello-world` extends Common {
      def moduleDeps = Seq(snunit)
    }
    object `multiple-handlers` extends Common {
      def moduleDeps = Seq(snunit)
    }
    object autowire extends Common {
      def moduleDeps = Seq(`snunit-autowire`)
    }
    object async extends Common {
      def moduleDeps = Seq(`snunit-async`)
    }
    object `async-multiple-handlers` extends Common {
      def moduleDeps = Seq(`snunit-async`)
    }
    object stream extends Common {
      def moduleDeps = Seq(snunit)
      def ivyDeps = T { super.ivyDeps() ++ Seq(upickle) }
    }
    object routes extends Common {
      def moduleDeps = Seq(`snunit-routes`)
      def ivyDeps = T { super.ivyDeps() ++ Seq(upickle) }
    }
  }
  def scalaVersion = "2.13.3"
  object test extends Tests {
    def testFrameworks = Seq("utest.runner.Framework")
    def ivyDeps =
      Agg(
        ivy"com.lihaoyi::utest:0.7.5",
        ivy"com.lihaoyi::os-lib:0.7.1",
        ivy"com.lihaoyi::requests:0.6.5"
      )
  }
}

def buildSources = T(Seq(PathRef(os.pwd / "build.sc")))
