package build

import $ivy.`com.goyeau::mill-scalafix::0.3.1`
import $ivy.`io.chris-kipp::mill-ci-release::0.1.9`
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.7.1`
import $ivy.`com.github.lolgab::mill-crossplatform::0.2.3`
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:`
import $ivy.`com.github.lolgab::mill-mima::0.1.0`

import mill._, mill.scalalib._, mill.scalanativelib._, mill.scalanativelib.api._
import mill.scalalib.publish._
import com.goyeau.mill.scalafix.ScalafixModule
import io.kipp.mill.ci.release.CiReleaseModule
import de.tobiasroeser.mill.integrationtest._
import mill.contrib.buildinfo.BuildInfo
import com.github.lolgab.mill.mima._
import com.github.lolgab.mill.crossplatform._
import versions.Versions

val scalaVersions = Seq(Versions.scala3)

val http4sVersions = Seq(Versions.http4s023, Versions.http4s1)

val http4sAndScalaVersions = for {
  scalaV <- scalaVersions
  http4sV <- http4sVersions
} yield (scalaV, http4sV)

val osLib = ivy"com.lihaoyi::os-lib:${Versions.osLib}"
val upickle = ivy"com.lihaoyi::upickle::${Versions.upickle}"
val undertow = ivy"io.undertow:undertow-core:${Versions.undertow}"
val utest = ivy"com.lihaoyi::utest::${Versions.utest}"

val testServerPort = 8081

object Common {
  trait Shared extends ScalaModule with ScalafixModule {
    def organization = "com.github.lolgab"
    def name = "snunit"
    def crossScalaVersion: String

    def scalacOptions = super.scalacOptions() ++
      Seq("-deprecation")
  }
  trait SharedNative extends Shared with ScalaNativeModule {
    def scalaNativeVersion = versions.Versions.scalaNative

    def baseTestConfig(binary: os.Path) = {
      val appName = "app"
      ujson.Obj(
        "applications" -> ujson.Obj(
          appName -> ujson.Obj(
            "type" -> "external",
            "executable" -> binary.toString
          )
        ),
        "listeners" -> ujson.Obj(
          s"*:$testServerPort" -> ujson.Obj(
            "pass" -> s"applications/$appName"
          )
        )
      )
    }

    def deployTestApp() = Task.Command {
      val binary = nativeLink()
      unitd.runBackground(baseTestConfig(binary))
    }

  }

  trait Scala3Only extends SharedNative {
    def crossScalaVersion = Versions.scala3
    def scalaVersion = crossScalaVersion
  }
  trait Scala3OnlyJvm extends Shared {
    def crossScalaVersion = Versions.scala3
    def scalaVersion = crossScalaVersion
  }
  trait Cross extends CrossScalaModule with SharedNative
  trait CrossJvm extends CrossScalaModule with Shared
}

trait Publish extends CiReleaseModule with Mima {
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
  def mimaPreviousVersions = Seq("0.9.0")
  // Remove after first release with Scala Native 0.5
  def mimaPreviousArtifacts = Task { Seq.empty }
}
object snunit extends Cross[SNUnitModule](scalaVersions)
trait SNUnitModule extends Common.Cross with Publish {
  object test extends ScalaNativeTests with TestModule.Utest {
    def ivyDeps = super.ivyDeps() ++ Agg(utest)
  }
}

// object `snunit-async-cats-effect` extends Cross[SNUnitAsyncCatsEffectModule](scalaVersions)
// trait SNUnitAsyncCatsEffectModule extends Common.Cross with Publish {
//   def moduleDeps = Seq(snunit())

//   def ivyDeps =
//     Task {
//       super.ivyDeps() ++ Agg(
//         ivy"org.typelevel::cats-effect::${Versions.catsEffect}"
//       )
//     }
// }

object `snunit-undertow` extends Cross[SNUnitUndertow](scalaVersions)
trait SNUnitUndertow extends Common.Cross with Publish {
  def moduleDeps = Seq(snunit())
}

object `snunit-tapir` extends Cross[SNUnitTapirModule](scalaVersions)
trait SNUnitTapirModule extends Common.Cross with Publish {
  def moduleDeps = Seq(snunit())
  def ivyDeps = super.ivyDeps() ++ Agg(ivy"com.softwaremill.sttp.tapir::tapir-server::${Versions.tapir}")
}
// object `snunit-tapir-cats-effect` extends Cross[SNUnitTapirCatsEffect](scalaVersions)
// trait SNUnitTapirCatsEffect extends Common.Cross with Publish {
//   def moduleDeps = Seq(
//     `snunit-tapir`(),
//     `snunit-async-cats-effect`()
//   )
//   def ivyDeps = super.ivyDeps() ++ Agg(
//     ivy"com.softwaremill.sttp.tapir::tapir-cats-effect::${Versions.tapir}"
//   )
// }

// object `snunit-http4s` extends Cross[SNUnitHttp4s](http4sAndScalaVersions)
// trait SNUnitHttp4s extends Common.Cross with Cross.Module2[String, String] with Publish {
//   val http4sVersion = crossValue2
//   def moduleDeps = Seq(
//     snunit(),
//     `snunit-async-cats-effect`()
//   )
//   val http4sBinaryVersion = http4sVersion match {
//     case s"0.23.$_" => "0.23"
//     case s"1.$_"    => "1"
//   }
//   def artifactName = s"snunit-http4s$http4sBinaryVersion"
//   def ivyDeps = super.ivyDeps() ++ Agg(
//     ivy"org.http4s::http4s-server::$http4sVersion"
//   )
//   def sources = T.sources {
//     super.sources() ++ Agg(PathRef(millSourcePath / s"http4s-$http4sBinaryVersion" / "src"))
//   }
// }

def caskSources = Task {
  val dest = T.dest
  os.proc("git", "clone", "--branch", Versions.cask, "--depth", "1", "https://github.com/com-lihaoyi/cask", dest).call()
  os.proc("git", "apply", T.workspace / "cask.patch").call(cwd = dest / "cask")
  PathRef(dest)
}
def castorSources = Task {
  val dest = T.dest
  os.proc("git", "clone", "--branch", Versions.castor, "--depth", "1", "https://github.com/com-lihaoyi/castor", dest)
    .call()
  PathRef(dest)
}
object `snunit-cask` extends Cross[SNUnitCaskModule](scalaVersions)
trait SNUnitCaskModule extends Common.Cross with Publish {
  override def generatedSources = Task {
    val cask = caskSources().path / "cask"
    val castor = castorSources().path / "castor"
    Seq(cask / "src", cask / "util" / "src", cask / "src-3", castor / "src", castor / "src-js-native").map(PathRef(_))
  }
  def moduleDeps = Seq(`snunit-undertow`(crossScalaVersion))
  def ivyDeps = super.ivyDeps() ++ Agg(
    upickle,
    ivy"io.github.cquiroz::scala-java-time::${Versions.scalaJavaTime}",
    ivy"com.lihaoyi::pprint::${Versions.pprint}"
  )
}

object integration extends ScalaModule {
  object tests extends Module {
    object `hello-world` extends Cross[HelloWorld](scalaVersions)
    trait HelloWorld extends Common.Cross {
      def moduleDeps = Seq(snunit())
    }
    object `websocket-echo` extends Common.Scala3Only {
      def moduleDeps = Seq(snunit(crossScalaVersion))
    }
    object `multiple-handlers` extends Common.Scala3Only {
      def moduleDeps = Seq(snunit(crossScalaVersion))
    }
    object `undertow-helloworld` extends CrossPlatform {
      object native extends CrossPlatformScalaModule with Common.Scala3Only {
        def moduleDeps = Seq(`snunit-undertow`(crossScalaVersion))
      }
      object jvm extends CrossPlatformScalaModule with Common.Scala3OnlyJvm {
        def ivyDeps = super.ivyDeps() ++ Agg(undertow)
      }
    }
    object `cask-helloworld` extends CrossPlatform {
      object jvm extends CrossPlatformScalaModule with Common.Scala3OnlyJvm {
        def ivyDeps = super.ivyDeps() ++ Agg(
          ivy"com.lihaoyi::cask:${Versions.cask}"
        )
      }
      object native extends CrossPlatformScalaModule with Common.Scala3Only {
        def moduleDeps = Seq(`snunit-cask`(crossScalaVersion))
      }
    }
    object `tapir-helloworld` extends Common.Scala3Only {
      override def moduleDeps = Seq(`snunit-tapir`(crossScalaVersion))
    }
    // object `tapir-app` extends Common.Scala3Only {
    //   override def moduleDeps = Seq(`snunit-tapir-cats-effect`(crossScalaVersion))
    // }
    // object `http4s-helloworld` extends Cross[Http4sHelloWorldModule](http4sVersions)
    // trait Http4sHelloWorldModule extends Common.Scala3Only with Cross.Module[String] {
    //   def http4sVersion = crossValue
    //   def moduleDeps = Seq(
    //     `snunit-http4s`(crossScalaVersion, http4sVersion)
    //   )
    //   def ivyDeps = super.ivyDeps() ++ Agg(
    //     ivy"org.http4s::http4s-dsl::$http4sVersion"
    //   )
    // }
    // object `http4s-app` extends Common.Scala3Only {
    //   val http4sVersion = Versions.http4s1
    //   def moduleDeps = Seq(
    //     `snunit-http4s`(crossScalaVersion, http4sVersion)
    //   )
    //   def ivyDeps = super.ivyDeps() ++ Agg(
    //     ivy"org.http4s::http4s-dsl::$http4sVersion"
    //   )
    // }
    // object `tapir-helloworld-cats-effect` extends Common.Scala3Only {
    //   def moduleDeps = Seq(
    //     `snunit-tapir-cats-effect`(crossScalaVersion)
    //   )
    // }
  }
  def scalaVersion = Versions.scala3
  object test extends ScalaTests with TestModule.Utest with BuildInfo {
    def buildInfoMembers = Seq(
      BuildInfo.Value("port", testServerPort.toString),
      BuildInfo.Value("scalaVersions", scalaVersions.mkString(":")),
      BuildInfo.Value("http4sVersions", http4sVersions.mkString(":")),
      BuildInfo.Value("unitControl", unitd.control.toString)
    )
    def buildInfoPackageName = "snunit.test"
    def ivyDeps =
      Agg(
        utest,
        osLib,
        ivy"com.softwaremill.sttp.client3::core:${Versions.sttp}"
      )
  }
}

object `snunit-plugins-shared` extends Cross[SnunitPluginsShared](mill.main.BuildInfo.scalaVersion, Versions.scala212)
trait SnunitPluginsShared extends CrossScalaModule with Publish with BuildInfo {
  def buildInfoMembers = Seq(
    BuildInfo.Value("snunitVersion", publishVersion())
  )
  def buildInfoPackageName = "snunit.plugin.internal"
  object test extends ScalaTests with TestModule.Utest {
    def ivyDeps = super.ivyDeps() ++ Agg(
      utest,
      osLib
    )
  }
}
object `snunit-mill-plugin` extends ScalaModule with Publish {
  def artifactName = s"mill-snunit_mill${Versions.mill011.split('.').take(2).mkString(".")}"
  def moduleDeps = Seq(`snunit-plugins-shared`(mill.main.BuildInfo.scalaVersion))
  def scalaVersion = mill.main.BuildInfo.scalaVersion
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    ivy"com.lihaoyi::mill-scalanativelib:${Versions.mill011}"
  )
}
object `snunit-mill-plugin-itest` extends MillIntegrationTestModule {
  def millTestVersion = Versions.mill011
  def pluginsUnderTest = Seq(`snunit-mill-plugin`)
  def temporaryIvyModules = Seq(`snunit-plugins-shared`(mill.main.BuildInfo.scalaVersion), snunit(Versions.scala3))
}
