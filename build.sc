import mill._, mill.scalalib._, mill.scalanativelib._, mill.scalanativelib.api._
import mill.scalalib.api.ZincWorkerUtil.isScala3
import mill.scalalib.publish._
import $ivy.`com.goyeau::mill-scalafix::0.2.8`
import com.goyeau.mill.scalafix.ScalafixModule
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.1.4`
import de.tobiasroeser.mill.vcs.version.VcsVersion
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:`
import mill.contrib.buildinfo.BuildInfo
import $file.versions
import $file.unitd

val upickle = ivy"com.lihaoyi::upickle::1.5.0"
val undertow = ivy"io.undertow:undertow-core:2.2.14.Final"

val scala213 = "2.13.8"
val scala3 = "3.1.1"
val scalaVersions = Seq(scala213, scala3)

val testServerPort = 8081

object Common {
  trait Shared extends ScalaModule with ScalafixModule {
    def organization = "com.github.lolgab"
    def name = "snunit"
    def crossScalaVersion: String

    def scalacOptions = super.scalacOptions() ++ (if (isScala3(crossScalaVersion)) Seq() else Seq("-Ywarn-unused"))

    def scalafixIvyDeps = Agg(ivy"com.github.liancheng::organize-imports:0.6.0")

    override def sources = T.sources { super.sources() ++ Seq(PathRef(millSourcePath / os.up / "shared" / "src")) }
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
    def crossScalaVersion = scala213
    def scalaVersion = crossScalaVersion
  }
  trait Cross extends SharedNative with CrossScalaModule
  trait CrossJvm extends Shared with CrossScalaModule
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

object snunit extends Module {
  object native extends Cross[SNUnitNativeModule](scalaVersions: _*)
  class SNUnitNativeModule(val crossScalaVersion: String) extends Common.Cross with Publish

  object jvm extends Cross[SNUnitJvmModule](scalaVersions: _*)
  class SNUnitJvmModule(val crossScalaVersion: String) extends Common.CrossJvm with Publish {
    def ivyDeps = Agg(undertow)
  }
}

object `snunit-async` extends Cross[SNUnitAsyncModule](scalaVersions: _*)
class SNUnitAsyncModule(val crossScalaVersion: String) extends Common.Cross with Publish {
  def moduleDeps = Seq(snunit.native(crossScalaVersion))

  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"com.github.lolgab::native-loop-core::0.2.1"
      )
    }
}

object `snunit-routes` extends Common.Scala2Only with Publish {
  def moduleDeps = Seq(snunit.native(crossScalaVersion))

  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"tech.sparse::trail::0.3.1"
      )
    }
}

object `snunit-autowire` extends Common.Scala2Only with Publish {
  def moduleDeps = Seq(`snunit-async`(crossScalaVersion))
  def ivyDeps =
    T {
      super.ivyDeps() ++ Agg(
        ivy"com.lihaoyi::autowire::0.3.3",
        upickle
      )
    }
}

object `snunit-undertow` extends Cross[SNUnitUndertow](scalaVersions: _*)
class SNUnitUndertow(val crossScalaVersion: String) extends Common.Cross with Publish {
  def moduleDeps = Seq(snunit.native(crossScalaVersion))
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
  def moduleDeps = Seq(`snunit-undertow`(crossScalaVersion))
  def ivyDeps = super.ivyDeps() ++ Agg(
    upickle,
    ivy"com.lihaoyi::castor::0.2.1",
    ivy"org.ekrich::sjavatime::1.1.9",
    ivy"com.lihaoyi::pprint::0.6.6"
  )
}

object integration extends ScalaModule {
  object tests extends Module {
    object `hello-world` extends Module {
      object native extends Cross[HelloWorldNativeModule](scalaVersions: _*)
      class HelloWorldNativeModule(val crossScalaVersion: String) extends Common.Cross {
        def millSourcePath = super.millSourcePath / os.up
        def moduleDeps = Seq(snunit.native(crossScalaVersion))
      }
      object jvm extends Cross[HelloWorldJvmModule](scalaVersions: _*)
      class HelloWorldJvmModule(val crossScalaVersion: String) extends Common.CrossJvm {
        def millSourcePath = super.millSourcePath / os.up
        def moduleDeps = Seq(snunit.jvm(crossScalaVersion))
      }
    }
    object `empty-response` extends Common.Scala2Only {
      def moduleDeps = Seq(snunit.native(crossScalaVersion))
    }
    object `multiple-handlers` extends Common.Scala2Only {
      def moduleDeps = Seq(snunit.native(crossScalaVersion))
    }
    object autowire extends Common.Scala2Only {
      def moduleDeps = Seq(`snunit-autowire`)
    }
    object `autowire-int` extends Common.Scala2Only {
      def moduleDeps = Seq(`snunit-autowire`)
    }
    object async extends Cross[AsyncModule](scalaVersions: _*)
    class AsyncModule(val crossScalaVersion: String) extends Common.Cross {
      def moduleDeps = Seq(`snunit-async`(crossScalaVersion))
    }
    object `async-multiple-handlers` extends Cross[AsyncMultipleHandlersModule](scalaVersions: _*)
    class AsyncMultipleHandlersModule(val crossScalaVersion: String) extends Common.Cross {
      def moduleDeps = Seq(`snunit-async`(crossScalaVersion))
    }
    object routes extends Common.Scala2Only {
      def moduleDeps = Seq(`snunit-routes`)
      def ivyDeps = T { super.ivyDeps() ++ Seq(upickle) }
    }
    object `handlers-composition` extends Cross[HandlersCompositionModule](scalaVersions: _*)
    class HandlersCompositionModule(val crossScalaVersion: String) extends Common.Cross {
      def moduleDeps = Seq(snunit.native(crossScalaVersion))
    }
    object `undertow-helloworld` extends Module {
      object jvm extends Cross[JvmModule](scalaVersions: _*)
      class JvmModule(val crossScalaVersion: String) extends CrossScalaModule {
        def millSourcePath = super.millSourcePath / os.up
        def scalaVersion = scala213
        def ivyDeps = super.ivyDeps() ++ Agg(undertow)
      }
      object native extends Cross[NativeModule](scalaVersions: _*)
      class NativeModule(val crossScalaVersion: String) extends Common.Cross {
        def millSourcePath = super.millSourcePath / os.up
        def moduleDeps = Seq(`snunit-undertow`(crossScalaVersion))
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

object `snunit-plugins-shared` extends Cross[SnunitPluginsShared](scala213, versions.Versions.scala212)
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
