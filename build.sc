import mill._, mill.scalalib._, mill.scalanativelib._, mill.scalanativelib.api._
import mill.scalalib.api.ZincWorkerUtil.isScala3
import mill.scalalib.publish._
import $ivy.`com.goyeau::mill-scalafix::0.2.8`
import com.goyeau.mill.scalafix.ScalafixModule
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.1.4`
import de.tobiasroeser.mill.vcs.version.VcsVersion
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:`
import mill.contrib.buildinfo.BuildInfo

val upickle = ivy"com.lihaoyi::upickle::1.4.3"

val scala213 = "2.13.8"
val scala3 = "3.1.0"
val scalaVersions = Seq(scala213, scala3)

val testServerPort = 8081

object Common {
  trait Shared extends ScalaNativeModule with ScalafixModule {
    def organization = "com.github.lolgab"
    def name = "snunit"

    def crossScalaVersion: String
    def scalaNativeVersion = "0.4.3"

    val unitSocketPath = sys.env
      .getOrElse(
        "UNIT_CONTROL_SOCKET_PATH",
        sys.props("os.name") match {
          case "Linux"    => "/var/run/control.unit.sock"
          case "Mac OS X" => "/usr/local/var/run/unit/control.sock"
        }
      )

    def baseTestConfig(binary: os.Path, numProcesses: Int) = {
      val appName = "test_app"
      ujson.Obj(
        "applications" -> ujson.Obj(
          appName -> ujson.Obj(
            "type" -> "external",
            "executable" -> binary.toString,
            "processes" -> numProcesses,
            "limits" -> ujson.Obj(
              "timeout" -> 1
            )
          )
        ),
        "listeners" -> ujson.Obj(
          s"*:$testServerPort" -> ujson.Obj(
            "pass" -> s"applications/$appName"
          )
        )
      )
    }

    def deployTestApp() = {
      def doCurl(json: ujson.Value) = {
        os.proc(
          "curl",
          "-X",
          "PUT",
          "--data-binary",
          json.toString,
          "--unix-socket",
          unitSocketPath,
          "http://localhost/config"
        ).call()
      }
      T.command {
        val binary = nativeLink()
        doCurl(baseTestConfig(binary = binary, numProcesses = 0))
        doCurl(baseTestConfig(binary = binary, numProcesses = sys.runtime.availableProcessors))
      }
    }

    def scalacOptions = super.scalacOptions() ++ (if(isScala3(crossScalaVersion)) Seq() else Seq("-Ywarn-unused"))

    def scalafixIvyDeps = Agg(ivy"com.github.liancheng::organize-imports:0.6.0")
  }

  trait Scala2Only extends Shared {
    def crossScalaVersion = scala213
    def scalaVersion = crossScalaVersion
  }
  trait Cross extends Shared with CrossScalaModule
}

trait Publish extends PublishModule {
  def pomSettings =
    PomSettings(
      description = "Scala Native server using NGINX Unit",
      organization = "com.github.lolgab",
      url = "https://github.com/lolgab/snunit",
      licenses = Seq(License.MIT),
      versionControl = VersionControl.github(owner = "lolgab", repo = "snunit"),
      developers = Seq(
        Developer("lolgab", "Lorenzo Gabriele", "https://github.com/lolgab")
      )
    )
  def publishVersion = VcsVersion.vcsState().format()
}

object snunit extends Cross[SNUnitModule](scalaVersions: _*)
class SNUnitModule(val crossScalaVersion: String) extends Common.Cross with Publish

object `snunit-async` extends Common.Scala2Only with Publish {
  def moduleDeps = Seq(snunit(crossScalaVersion))

  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"com.github.lolgab::native-loop-core::0.2.0"
      )
    }
}

object `snunit-routes` extends Common.Scala2Only with Publish {
  def moduleDeps = Seq(snunit(crossScalaVersion))

  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"tech.sparse::trail::0.3.1"
      )
    }
}

object `snunit-autowire` extends Common.Scala2Only with Publish {
  def moduleDeps = Seq(`snunit-async`)
  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"com.lihaoyi::autowire::0.3.3",
        upickle
      )
    }
}

object `snunit-undertow` extends Common.Scala2Only with Publish {
  def moduleDeps = Seq(snunit(crossScalaVersion))
}

def caskSources = T {
  val dest = T.dest
  os.proc("git", "clone", "--branch", "0.8.0", "--depth", "1", "https://github.com/com-lihaoyi/cask", dest).call()
  PathRef(dest)
}
object `snunit-cask` extends Common.Scala2Only with Publish {
  override def generatedSources = T {
    val cask = caskSources().path / "cask"
    val util = cask / "util"
    Seq(cask / "src", cask / "src-2", util / "src").map(PathRef(_))
  }
  def moduleDeps = Seq(`snunit-undertow`)
  def ivyDeps = super.ivyDeps() ++ Agg(
    upickle,
    ivy"com.lihaoyi::castor::0.2.0",
    ivy"org.ekrich::sjavatime::1.1.5",
    ivy"com.lihaoyi::pprint::0.6.6"
  )
}

object integration extends ScalaModule {
  object tests extends Module {
    object `hello-world` extends Cross[HelloWorldModule](scalaVersions: _*)
    class HelloWorldModule(val crossScalaVersion: String) extends Common.Cross {
      def moduleDeps = Seq(snunit(crossScalaVersion))
    }
    object `empty-response` extends Common.Scala2Only {
      def moduleDeps = Seq(snunit(crossScalaVersion))
    }
    object `multiple-handlers` extends Common.Scala2Only {
      def moduleDeps = Seq(snunit(crossScalaVersion))
    }
    object autowire extends Common.Scala2Only {
      def moduleDeps = Seq(`snunit-autowire`)
    }
    object `autowire-int` extends Common.Scala2Only {
      def moduleDeps = Seq(`snunit-autowire`)
    }
    object async extends Common.Scala2Only {
      def moduleDeps = Seq(`snunit-async`)
    }
    object `async-multiple-handlers` extends Common.Scala2Only {
      def moduleDeps = Seq(`snunit-async`)
    }
    object routes extends Common.Scala2Only {
      def moduleDeps = Seq(`snunit-routes`)
      def ivyDeps = T { super.ivyDeps() ++ Seq(upickle) }
    }
    object `handlers-composition` extends Common.Scala2Only {
      def moduleDeps = Seq(snunit(crossScalaVersion))
    }
    object `undertow-helloworld` extends Module {
      object jvm extends ScalaModule {
        def millSourcePath = super.millSourcePath / os.up
        def scalaVersion = scala213
        def ivyDeps = super.ivyDeps() ++ Agg(
          ivy"io.undertow:undertow-core:2.2.10.Final"
        )
      }
      object native extends Common.Scala2Only {
        def millSourcePath = super.millSourcePath / os.up
        def moduleDeps = Seq(`snunit-undertow`)
      }
    }
    object `cask-helloworld` extends Module {
      object jvm extends ScalaModule {
        def millSourcePath = super.millSourcePath / os.up
        def scalaVersion = scala213
        def ivyDeps = super.ivyDeps() ++ Agg(
          ivy"com.lihaoyi::cask:0.7.11"
        )
      }
      object native extends Common.Scala2Only {
        def millSourcePath = super.millSourcePath / os.up
        def moduleDeps = Seq(`snunit-cask`)
      }
    }
  }
  def scalaVersion = scala213
  object test extends Tests with TestModule.Utest with BuildInfo {
    def buildInfoMembers = Map(
      "port" -> testServerPort.toString,
      "scalaVersions" -> scalaVersions.mkString(",")
    )
    def ivyDeps =
      Agg(
        ivy"com.lihaoyi::utest:0.7.7",
        ivy"com.lihaoyi::os-lib:0.7.8",
        ivy"com.lihaoyi::requests:0.6.5"
      )
  }
}

object `snunit-plugins-shared` extends Cross[SnunitPluginsShared](scala213, "2.12.13")
class SnunitPluginsShared(val crossScalaVersion: String) extends CrossScalaModule with Publish {
  object test extends Tests with TestModule.Utest {
    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"com.lihaoyi::utest:0.7.7",
      ivy"com.lihaoyi::os-lib:0.7.8"
    )
  }
}

object `snunit-mill-plugin` extends ScalaModule with Publish {
  def moduleDeps = Seq(`snunit-plugins-shared`(scala213))
  def scalaVersion = scala213
  def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.lihaoyi::mill-scalanativelib:0.9.8"
  )
}

def buildSources = T(Seq(PathRef(os.pwd / "build.sc")))
