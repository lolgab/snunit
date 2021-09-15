import mill._, mill.scalalib._, mill.scalanativelib._, mill.scalanativelib.api._
import mill.scalalib.publish._
import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
import $ivy.`com.goyeau::mill-scalafix:0.2.4`
import com.goyeau.mill.scalafix.ScalafixModule
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version_mill0.9:0.1.1`
import de.tobiasroeser.mill.vcs.version.VcsVersion

val upickle = ivy"com.lihaoyi::upickle::1.3.0"

val scalaV = "2.13.4"

trait Common extends ScalaNativeModule with ScalafixModule {
  def organization = "com.github.lolgab"
  def name = "snunit"

  def scalaVersion = scalaV
  def scalaNativeVersion = "0.4.0"

  val unitSocketPath = sys.env
    .getOrElse(
      "UNIT_CONTROL_SOCKET_PATH",
      sys.props("os.name") match {
        case "Linux"    => "/var/run/control.unit.sock"
        case "Mac OS X" => "/usr/local/var/run/unit/control.sock"
      }
    )

  def baseTestConfig(binary: os.Path, numProcesses: Int, appName: String = "test_app", port: Int = 8081) =
    ujson.Obj(
      "applications" -> ujson.Obj(
        appName -> ujson.Obj(
          "type" -> "external",
          "working_directory" -> (binary / os.up).toString,
          "executable" -> binary.last,
          "processes" -> numProcesses,
          "limits" -> ujson.Obj(
            "timeout" -> 1
          )
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
      doCurl(baseTestConfig(binary = binary, numProcesses = sys.runtime.availableProcessors))
    }
  }

  def scalacOptions = Seq("-Ywarn-unused")

  def scalafixIvyDeps = Agg(ivy"com.github.liancheng::organize-imports:0.4.4")
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
        Developer("lolgab", "Lorenzo Gabriele", "https://github.com/lolgab")
      )
    )
  def publishVersion = VcsVersion.vcsState().format()
}

object snunit extends Common with Publish {
  def ivyDeps = T { super.ivyDeps() ++ Seq(ivy"com.lihaoyi::geny::0.6.6") }
}

object `snunit-async` extends Common with Publish {
  def moduleDeps = Seq(snunit)

  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"com.github.lolgab::native-loop-core::0.2.0"
      )
    }
}

object `snunit-routes` extends Common with Publish {
  def moduleDeps = Seq(snunit)

  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"tech.sparse::trail::0.3.1"
      )
    }
}

object `snunit-autowire` extends Common with Publish {
  def moduleDeps = Seq(`snunit-async`)
  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"com.lihaoyi::autowire::0.3.3",
        upickle
      )
    }
}

object `snunit-zio` extends Common with Publish {
  def moduleDeps = Seq(`snunit-async`)
  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"dev.zio::zio::1.0.4-2"
      )
    }
}

object `snunit-undertow` extends Common with Publish {
  def moduleDeps = Seq(snunit)
}

def caskSources = T {
  val dest = T.dest
  os.proc("git", "clone", "https://github.com/com-lihaoyi/cask", dest).call()
  PathRef(dest)  
}
object cask extends Common {
  override def generatedSources = T {
    val cask = caskSources().path / "cask"
    val util = cask / "util"
    Seq(cask / "src", cask / "src-2", util / "src").map(PathRef(_))
  }
  def moduleDeps = Seq(`snunit-undertow`)
  def ivyDeps = super.ivyDeps() ++ Agg(
    upickle,
    ivy"com.lihaoyi::castor::0.2.0",
    ivy"org.ekrich::sjavatime::1.1.2",
    ivy"com.lihaoyi::pprint::0.6.2"
  )
}

object integration extends ScalaModule {
  object tests extends Module {
    object `hello-world` extends Common {
      def moduleDeps = Seq(snunit)
    }
    object `empty-response` extends Common {
      def moduleDeps = Seq(snunit)
    }
    object `multiple-handlers` extends Common {
      def moduleDeps = Seq(snunit)
    }
    object autowire extends Common {
      def moduleDeps = Seq(`snunit-autowire`)
    }
    object `autowire-int` extends Common {
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
    object `handlers-composition` extends Common {
      def moduleDeps = Seq(snunit)
    }
    object zio extends Common {
      def moduleDeps = Seq(`snunit-zio`)
    }

    object `undertow-helloworld` extends Module {
      object jvm extends ScalaModule {
        def millSourcePath = super.millSourcePath / os.up
        def scalaVersion = scalaV
        def ivyDeps = super.ivyDeps() ++ Agg(
          ivy"io.undertow:undertow-core:2.2.10.Final"
        )
      }
      object native extends Common {
        def millSourcePath = super.millSourcePath / os.up
        def moduleDeps = Seq(`snunit-undertow`)
      }
    }
    object `cask-helloworld` extends Module {
      object jvm extends ScalaModule {
        def millSourcePath = super.millSourcePath / os.up
        def scalaVersion = scalaV
        def ivyDeps = super.ivyDeps() ++ Agg(
          ivy"com.lihaoyi::cask:0.7.11"
        )
      }
      object native extends Common {
        def millSourcePath = super.millSourcePath / os.up
        def moduleDeps = Seq(cask)
      }
    }

    object caskTests extends Common {
      def moduleDeps = Seq(cask)
      override def generatedSources = T {
        val cask = caskSources().path
        Seq(PathRef(cask / "example" / "compress" / "app" / "src"))
      }
    }
  }
  def scalaVersion = scalaV
  object test extends Tests with TestModule.Utest {
    def ivyDeps =
      Agg(
        ivy"com.lihaoyi::utest:0.7.7",
        ivy"com.lihaoyi::os-lib:0.7.8",
        ivy"com.lihaoyi::requests:0.6.5"
      )
  }
}

object `snunit-plugins-shared` extends Cross[SnunitPluginsShared]("2.13.4", "2.12.14")
class SnunitPluginsShared(val crossScalaVersion: String) extends CrossScalaModule with Publish {
  object test extends Tests with TestModule.Utest {
    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"com.lihaoyi::utest:0.7.7",
      ivy"com.lihaoyi::os-lib:0.7.8"
    )
  }
}

object `snunit-mill-plugin` extends ScalaModule with Publish {
  def moduleDeps = Seq(`snunit-plugins-shared`("2.13.4"))
  def scalaVersion = "2.13.4"
  def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.lihaoyi::mill-scalanativelib:0.9.8"
  )
}

def buildSources = T(Seq(PathRef(os.pwd / "build.sc")))
