import mill._, mill.scalalib._, mill.scalanativelib._, mill.scalanativelib.api._
import mill.scalalib.api.ZincWorkerUtil.isScala3
import mill.scalalib.publish._
import $ivy.`com.goyeau::mill-scalafix::0.2.11`
import com.goyeau.mill.scalafix.ScalafixModule
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.2.0`
import de.tobiasroeser.mill.vcs.version.VcsVersion
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.6.1`
import de.tobiasroeser.mill.integrationtest._
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:`
import mill.contrib.buildinfo.BuildInfo
import $ivy.`com.github.lolgab::mill-mima::0.0.13`
import com.github.lolgab.mill.mima._
import $ivy.`com.github.lolgab::mill-crossplatform::0.0.3`
import com.github.lolgab.mill.crossplatform._
import $file.versions
import versions.Versions
import $file.unitd

val scalaVersions = Seq(Versions.scala213, Versions.scala3)

val http4sVersions = for {
  scalaV <- scalaVersions
  http4sV <- Seq(Versions.http4s023, Versions.http4s1)
} yield (scalaV, http4sV)

val upickle = ivy"com.lihaoyi::upickle::${Versions.upickle}"
val undertow = ivy"io.undertow:undertow-core:${Versions.undertow}"

val testServerPort = 8081

object Common {
  trait Shared extends ScalaModule with ScalafixModule {
    def organization = "com.github.lolgab"
    def name = "snunit"
    def crossScalaVersion: String

    def scalacOptions = super.scalacOptions() ++ (if (isScala3(crossScalaVersion)) Seq() else Seq("-Ywarn-unused"))

    def scalafixIvyDeps = Agg(ivy"com.github.liancheng::organize-imports:0.6.0")
  }
  trait SharedNative extends Shared with ScalaNativeModule {
    def scalaNativeVersion = versions.Versions.scalaNative

    def baseTestConfig(binary: os.Path) = {
      val appName = "app"
      ujson.Obj(
        "applications" -> ujson.Obj(
          appName -> ujson.Obj(
            "type" -> "external",
            "executable" -> binary.toString,
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

    def deployTestApp() = T.command {
      val binary = nativeLink()
      unitd.runBackground(baseTestConfig(binary))
    }

  }

  trait Scala2Only extends SharedNative {
    def crossScalaVersion = Versions.scala213
    def scalaVersion = crossScalaVersion
  }
  trait Cross extends SharedNative with CrossScalaModule
  trait CrossJvm extends Shared with CrossScalaModule
}

trait Publish extends PublishModule with Mima {
  def pomSettings =
    PomSettings(
      description = "Scala Native server using NGINX Unit",
      organization = "com.github.lolgab",
      url = "https://github.com/lolgab/snunit",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github(owner = "lolgab", repo = "snunit"),
      developers = Seq(
        Developer("lolgab", "Lorenzo Gabriele", "https://github.com/lolgab")
      )
    )
  def publishVersion = VcsVersion.vcsState().format()
  def mimaPreviousVersions = Seq("0.2.1")
  def mimaBinaryIssueFilters = Seq(
    // snunit.Request is not meant for extension. The only
    // valid implementations are `RequestImpl`s in this repo.
    ProblemFilter.exclude[ReversedMissingMethodProblem]("snunit.Request.*"),
    ProblemFilter.exclude[Problem]("snunit.unsafe.*")
  )
}
object `snunit-internal-api` extends JavaModule
object snunit extends Cross[SNUnitModule](scalaVersions: _*)
class SNUnitModule(val crossScalaVersion: String) extends CrossPlatform {
  trait Shared extends CrossPlatformCrossScalaModule with Publish

  object native extends Shared with Common.Cross {
    def compileModuleDeps = Seq(`snunit-internal-api`)
  }
  object jvm extends Shared with Common.CrossJvm {
    def ivyDeps = super.ivyDeps() ++ Agg(undertow)
  }
}

object `snunit-async-loop` extends Cross[SNUnitAsyncModule](scalaVersions: _*)
class SNUnitAsyncModule(val crossScalaVersion: String) extends Common.Cross with Publish {
  def moduleDeps = Seq(`snunit`(crossScalaVersion).native)

  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"com.github.lolgab::native-loop-core::${Versions.scalaNativeLoop}"
      )
    }
}

object `snunit-async-epollcat` extends Cross[SNUnitAsyncEpollcatModule](scalaVersions: _*)
class SNUnitAsyncEpollcatModule(val crossScalaVersion: String) extends Common.Cross with Publish {
  def moduleDeps = Seq(`snunit`(crossScalaVersion).native)

  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"com.armanbilge::epollcat::${Versions.epollcat}"
      )
    }
}

object `snunit-autowire` extends Common.Scala2Only with Publish {
  def moduleDeps = Seq(`snunit-async-loop`(crossScalaVersion))
  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"com.lihaoyi::autowire::${Versions.autowire}",
        upickle
      )
    }
}

object `snunit-undertow` extends Cross[SNUnitUndertow](scalaVersions: _*)
class SNUnitUndertow(val crossScalaVersion: String) extends Common.Cross with Publish {
  def moduleDeps = Seq(snunit(crossScalaVersion).native)
}

object `snunit-tapir` extends Cross[SNUnitTapirModule](scalaVersions: _*)
class SNUnitTapirModule(val crossScalaVersion: String) extends CrossPlatform {
  def moduleDeps = Seq(snunit(crossScalaVersion))
  trait Shared extends CrossPlatformCrossScalaModule with Publish {
    def ivyDeps = super.ivyDeps() ++ Agg(ivy"com.softwaremill.sttp.tapir::tapir-server::${Versions.tapir}")
  }

  object native extends Shared with Common.Cross
  object jvm extends Shared with Common.CrossJvm
}

object `snunit-http4s` extends Cross[SNUnitHttp4s](http4sVersions: _*)
class SNUnitHttp4s(val crossScalaVersion: String, http4sVersion: String) extends CrossPlatform {
  def moduleDeps = Seq(`snunit`(crossScalaVersion))
  object native extends CrossPlatformCrossScalaModule with Common.Cross with Publish {
    val http4sBinaryVersion = http4sVersion match {
      case s"0.23.$_" => "0.23"
      case s"1.$_"    => "1"
    }
    def artifactName = s"snunit-http4s$http4sBinaryVersion"
    def millSourcePath = super.millSourcePath / os.up
    def ivyDeps = super.ivyDeps() ++ Agg(ivy"org.http4s::http4s-server::$http4sVersion")
    def sources = T.sources {
      super.sources() ++ Agg(PathRef(millSourcePath / s"http4s-$http4sBinaryVersion" / "src"))
    }
  }
}

object `snunit-tapir-zio` extends Cross[SNUnitTapirZio](Versions.scala213)
class SNUnitTapirZio(val crossScalaVersion: String) extends CrossPlatform {
  def moduleDeps = Seq(`snunit-tapir`(crossScalaVersion))
  trait Shared extends CrossPlatformCrossScalaModule with Publish {
    def ivyDeps = super.ivyDeps() ++ Agg(ivy"dev.zio::zio::${Versions.zio}")
  }
  object native extends Shared with Common.Cross {
    def moduleDeps = Seq(`snunit-tapir`(crossScalaVersion).native)
  }
  object jvm extends Shared with Common.CrossJvm {
    def moduleDeps = Seq(`snunit-tapir`(crossScalaVersion).jvm)
  }
}

def caskSources = T {
  val dest = T.dest
  os.proc("git", "clone", "--branch", Versions.cask, "--depth", "1", "https://github.com/com-lihaoyi/cask", dest).call()
  os.proc("git", "apply", os.pwd / "cask.patch").call(cwd = dest / "cask")
  PathRef(dest)
}
object `snunit-cask` extends Cross[SNUnitCaskModule](scalaVersions: _*)
class SNUnitCaskModule(val crossScalaVersion: String) extends Common.Cross with Publish {
  override def generatedSources = T {
    val cask = caskSources().path / "cask"
    val util = cask / "util"
    val scala2 = cask / "src-2"
    val scala3 = cask / "src-3"
    val scalaVersionSpecific = if (isScala3(crossScalaVersion)) scala3 else scala2
    Seq(cask / "src", util / "src", scalaVersionSpecific).map(PathRef(_))
  }
  def moduleDeps = Seq(`snunit-undertow`(crossScalaVersion))
  def ivyDeps = super.ivyDeps() ++ Agg(
    upickle,
    ivy"com.lihaoyi::castor::${Versions.castor}",
    ivy"org.ekrich::sjavatime::${Versions.sjavatime}",
    ivy"com.lihaoyi::pprint::${Versions.pprint}"
  )
}

object integration extends ScalaModule {
  object tests extends Module {
    object `hello-world` extends Cross[HelloWorld](scalaVersions: _*)
    class HelloWorld(val crossScalaVersion: String) extends CrossPlatform {
      def moduleDeps = Seq(snunit(crossScalaVersion))
      object native extends CrossPlatformCrossScalaModule with Common.Cross
      object jvm extends CrossPlatformCrossScalaModule with Common.CrossJvm
    }
    object `empty-response` extends Common.Scala2Only {
      def moduleDeps = Seq(snunit(crossScalaVersion).native)
    }
    object `multiple-handlers` extends Common.Scala2Only {
      def moduleDeps = Seq(snunit(crossScalaVersion).native)
    }
    object autowire extends Common.Scala2Only {
      def moduleDeps = Seq(`snunit-autowire`)
    }
    object `autowire-int` extends Common.Scala2Only {
      def moduleDeps = Seq(`snunit-autowire`)
    }
    object async extends Cross[AsyncModule](scalaVersions: _*)
    class AsyncModule(val crossScalaVersion: String) extends Common.Cross {
      def moduleDeps = Seq(`snunit-async-loop`(crossScalaVersion))
    }
    object `async-epollcat` extends Cross[AsyncEpollcatModule](scalaVersions: _*)
    class AsyncEpollcatModule(val crossScalaVersion: String) extends Common.Cross {
      def moduleDeps = Seq(`snunit-async-epollcat`(crossScalaVersion))
    }
    object `async-multiple-handlers` extends Cross[AsyncMultipleHandlersModule](scalaVersions: _*)
    class AsyncMultipleHandlersModule(val crossScalaVersion: String) extends Common.Cross {
      def moduleDeps = Seq(`snunit-async-loop`(crossScalaVersion))
    }
    object `handlers-composition` extends Cross[HandlersCompositionModule](scalaVersions: _*)
    class HandlersCompositionModule(val crossScalaVersion: String) extends Common.Cross {
      def moduleDeps = Seq(snunit(crossScalaVersion).native)
    }
    object `undertow-helloworld` extends Cross[UndertowHelloworld](scalaVersions: _*)
    class UndertowHelloworld(val crossScalaVersion: String) extends CrossPlatform {
      def moduleDeps = Seq(snunit(crossScalaVersion))
      object native extends CrossPlatformCrossScalaModule with Common.Cross {
        def moduleDeps = Seq(`snunit-undertow`(crossScalaVersion))
      }
      object jvm extends CrossPlatformCrossScalaModule with Common.CrossJvm {
        def ivyDeps = super.ivyDeps() ++ Agg(undertow)
      }
    }
    object `cask-helloworld` extends Cross[CaskHelloWorld](scalaVersions: _*)
    class CaskHelloWorld(val crossScalaVersion: String) extends CrossPlatform {
      object jvm extends CrossPlatformCrossScalaModule with Common.CrossJvm {
        def ivyDeps = super.ivyDeps() ++ Agg(
          ivy"com.lihaoyi::cask:${Versions.cask}"
        )
      }
      object native extends CrossPlatformCrossScalaModule with Common.Cross {
        def moduleDeps = Seq(`snunit-cask`(crossScalaVersion))
      }
    }
    object `tapir-helloworld` extends Cross[TapirHelloWorld](scalaVersions: _*)
    class TapirHelloWorld(val crossScalaVersion: String) extends CrossPlatform {
      override def moduleDeps = Seq(`snunit-tapir`(crossScalaVersion))
      object jvm extends CrossPlatformCrossScalaModule with Common.CrossJvm
      object native extends CrossPlatformCrossScalaModule with Common.Cross
    }
    object `http4s-helloworld` extends Cross[Http4sHelloWorldModule](http4sVersions: _*)
    class Http4sHelloWorldModule(val crossScalaVersion: String, http4sVersion: String) extends Common.Cross {
      def millSourcePath = super.millSourcePath / os.up
      def moduleDeps = Seq(
        `snunit-http4s`(crossScalaVersion, http4sVersion).native,
        `snunit-async-epollcat`(crossScalaVersion)
      )
      def ivyDeps = super.ivyDeps() ++ Agg(
        ivy"org.http4s::http4s-dsl::$http4sVersion"
      )
    }
    object `tapir-helloworld-future` extends Cross[TapirHelloWorldFutureNative](scalaVersions: _*)
    class TapirHelloWorldFutureNative(val crossScalaVersion: String) extends Common.Cross {
      def moduleDeps = Seq(
        `snunit-async-loop`(crossScalaVersion),
        `snunit-tapir`(crossScalaVersion).native
      )
    }
  }
  def scalaVersion = Versions.scala213
  object test extends Tests with TestModule.Utest with BuildInfo {
    def buildInfoMembers = Map(
      "port" -> testServerPort.toString,
      "scalaVersions" -> scalaVersions.mkString(":"),
      "http4sVersions" -> http4sVersions.map { case (a, b) => s"$a,$b" }.mkString(":"),
      "scala213" -> Versions.scala213
    )
    def ivyDeps =
      Agg(
        ivy"com.lihaoyi::utest:${Versions.utest}",
        ivy"com.lihaoyi::os-lib:${Versions.osLib}",
        ivy"com.lihaoyi::requests:${Versions.requests}"
      )
  }
}

object `snunit-plugins-shared` extends Cross[SnunitPluginsShared](Versions.scala213, Versions.scala212)
class SnunitPluginsShared(val crossScalaVersion: String) extends CrossScalaModule with Publish {
  object test extends Tests with TestModule.Utest {
    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"com.lihaoyi::utest:${Versions.utest}",
      ivy"com.lihaoyi::os-lib:${Versions.osLib}"
    )
  }
}
object `snunit-mill-plugin` extends ScalaModule with Publish {
  def artifactName = s"mill-snunit_mill${Versions.mill.split('.').take(2).mkString(".")}"
  def moduleDeps = Seq(`snunit-plugins-shared`(Versions.scala213))
  def scalaVersion = Versions.scala213
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    ivy"com.lihaoyi::mill-scalanativelib:${Versions.mill}"
  )
  def mimaPreviousArtifacts = Agg.empty[Dep]
}
object `snunit-mill-plugin-itest` extends MillIntegrationTestModule {
  def millTestVersion = Versions.mill
  def pluginsUnderTest = Seq(`snunit-mill-plugin`)
  def temporaryIvyModules = Seq(`snunit-plugins-shared`(Versions.scala213))
}

def buildSources = T(Seq(PathRef(os.pwd / "build.sc")))
