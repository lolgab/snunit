import mill._, mill.scalalib._, mill.scalanativelib._, mill.scalanativelib.api._
import mill.scalalib.publish._
import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
import $ivy.`com.goyeau::mill-scalafix:0.2.5`
import com.goyeau.mill.scalafix.ScalafixModule
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.1.1`
import de.tobiasroeser.mill.vcs.version.VcsVersion
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:$MILL_VERSION`
import mill.contrib.buildinfo.BuildInfo

val upickle = ivy"com.lihaoyi::upickle::1.4.3"

val scalaV = "2.13.6"

val testServerPort = 8081

trait Common extends ScalaNativeModule with ScalafixModule {
  def organization = "com.github.lolgab"
  def name = "snunit"

  def scalaVersion = scalaV
  def scalaNativeVersion = "0.4.2"

  def deployTestApp() = T.command {
    val dest = T.dest
    val env = T.env
    os.proc("killall", "unitd").call(check = false)
    val binary = nativeLink()
    val json = ujson.Obj(
      "applications" -> ujson.Obj(
        "app" -> ujson.Obj(
          "type" -> "external",
          "executable" -> binary.toString,
          "limits" -> ujson.Obj(
            "timeout" -> 1
          )
        )
      ),
      "listeners" -> ujson.Obj(
        s"*:$testServerPort" -> ujson.Obj(
          "pass" -> "applications/app"
        )
      )
    )

    val stateDir = dest / "state"
    os.makeDir.all(stateDir)
    os.write.over(stateDir / "conf.json", json)

    val pidFile = dest / "unit.pid"
    os.write(pidFile, "")

    val controlFile = os.pwd / "out" / "control.sock"
    os.remove.all(controlFile)

    os.proc(
      "unitd",
      "--control",
      s"unix:$controlFile",
      "--pid",
      pidFile,
      "--log",
      "/dev/stdout",
      "--state",
      stateDir,
      "--tmp",
      dest / "tmp"
    ).call()
    ()
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

object snunit extends Common with Publish

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

object `snunit-undertow` extends Common with Publish {
  def moduleDeps = Seq(snunit)
}

def caskSources = T {
  val dest = T.dest
  os.proc("git", "clone", "--branch", "0.8.0", "--depth", "1", "https://github.com/com-lihaoyi/cask", dest).call()
  PathRef(dest)
}
object `snunit-cask` extends Common with Publish {
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
    object routes extends Common {
      def moduleDeps = Seq(`snunit-routes`)
      def ivyDeps = T { super.ivyDeps() ++ Seq(upickle) }
    }
    object `handlers-composition` extends Common {
      def moduleDeps = Seq(snunit)
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
        def moduleDeps = Seq(`snunit-cask`)
      }
    }
  }
  def scalaVersion = scalaV
  object test extends Tests with TestModule.Utest with BuildInfo {
    def buildInfoMembers = Map(
      "port" -> testServerPort.toString
    )
    def ivyDeps =
      Agg(
        ivy"com.lihaoyi::utest:0.7.7",
        ivy"com.lihaoyi::os-lib:0.7.8",
        ivy"com.lihaoyi::requests:0.6.5"
      )
  }
}

object `snunit-plugins-shared` extends Cross[SnunitPluginsShared]("2.13.6", "2.12.13")
class SnunitPluginsShared(val crossScalaVersion: String) extends CrossScalaModule with Publish {
  object test extends Tests with TestModule.Utest {
    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"com.lihaoyi::utest:0.7.7",
      ivy"com.lihaoyi::os-lib:0.7.8"
    )
  }
}

object `snunit-mill-plugin` extends ScalaModule with Publish {
  def moduleDeps = Seq(`snunit-plugins-shared`("2.13.6"))
  def scalaVersion = "2.13.6"
  def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.lihaoyi::mill-scalanativelib:0.9.8"
  )
}

def buildSources = T(Seq(PathRef(os.pwd / "build.sc")))
