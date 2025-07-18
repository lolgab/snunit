package build

import $ivy.`com.goyeau::mill-scalafix::0.3.1`
import $ivy.`io.chris-kipp::mill-ci-release::0.1.9`
import $ivy.`com.github.lolgab::mill-crossplatform::0.2.3`
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:`
import $ivy.`com.github.lolgab::mill-mima::0.1.1`

import mill._, mill.scalalib._, mill.scalanativelib._, mill.scalanativelib.api._
import mill.scalalib.publish._
import mill.util.Jvm
import com.goyeau.mill.scalafix.ScalafixModule
import io.kipp.mill.ci.release.CiReleaseModule
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

    def scalacOptions = super.scalacOptions() ++
      Seq("-deprecation")
  }
  trait Jvm extends Shared {
    def scalaVersion = Versions.scala3
  }
  trait Native extends Jvm with ScalaNativeModule {
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
object snunit extends Common.Native with Publish {
  object test extends ScalaNativeTests with TestModule.Utest {
    def ivyDeps = super.ivyDeps() ++ Agg(utest)
  }
}

// object `snunit-async-cats-effect` extends Common.Native with Publish {
//   def moduleDeps = Seq(snunit)

//   def ivyDeps =
//     Task {
//       super.ivyDeps() ++ Agg(
//         ivy"org.typelevel::cats-effect::${Versions.catsEffect}"
//       )
//     }
// }

object `snunit-undertow` extends Common.Native with Publish {
  def moduleDeps = Seq(snunit)
  def ivyDeps = super.ivyDeps() ++ Agg(undertow)
  // Remove class and tasty files
  override def jar = Task {
    Jvm.createJar(
      localClasspath().map(_.path).filter(os.exists),
      manifest(),
      (_, file) =>
        file.ext match {
          case "class" | "tasty" => false
          case _                 => true
        }
    )
  }
  // Sometimes it gives problems compiling since our internal API is different
  // than the java API in the class files.
  def zincIncrementalCompilation = false
}

object `snunit-tapir` extends Common.Native with Publish {
  def moduleDeps = Seq(snunit)
  def ivyDeps = super.ivyDeps() ++ Agg(ivy"com.softwaremill.sttp.tapir::tapir-server::${Versions.tapir}")
}
// object `snunit-tapir-cats-effect` extends Common.Native with Publish {
//   def moduleDeps = Seq(
//     `snunit-tapir`,
//     `snunit-async-cats-effect`
//   )
//   def ivyDeps = super.ivyDeps() ++ Agg(
//     ivy"com.softwaremill.sttp.tapir::tapir-cats-effect::${Versions.tapir}"
//   )
// }

// object `snunit-http4s` extends Cross[SNUnitHttp4s]
// trait SNUnitHttp4s extends Common.Native with Cross.Module[String] with Publish {
//   val http4sVersion = crossValue
//   def moduleDeps = Seq(
//     snunit,
//     `snunit-async-cats-effect`
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
  val dest = Task.dest
  os.proc("git", "clone", "--branch", Versions.cask, "--depth", "1", "https://github.com/com-lihaoyi/cask", dest).call()
  os.proc("git", "apply", T.workspace / "cask.patch").call(cwd = dest / "cask")
  PathRef(dest)
}
def castorSources = Task {
  val dest = Task.dest
  os.proc("git", "clone", "--branch", Versions.castor, "--depth", "1", "https://github.com/com-lihaoyi/castor", dest)
    .call()
  PathRef(dest)
}
object `snunit-cask` extends Common.Native with Publish {
  override def generatedSources = Task {
    val cask = caskSources().path / "cask"
    val castor = castorSources().path / "castor"
    Seq(cask / "src", cask / "util" / "src", cask / "src-3", castor / "src", castor / "src-js-native").map(PathRef(_))
  }
  def moduleDeps = Seq(`snunit-undertow`)
  def ivyDeps = super.ivyDeps() ++ Agg(
    upickle,
    ivy"io.github.cquiroz::scala-java-time::${Versions.scalaJavaTime}",
    ivy"com.lihaoyi::pprint::${Versions.pprint}"
  )
}

object integration extends ScalaModule {
  object tests extends Module {
    object `hello-world` extends Common.Native {
      def moduleDeps = Seq(snunit)
    }
    object `websocket-echo` extends Common.Native {
      def moduleDeps = Seq(snunit)
    }
    object `multiple-handlers` extends Common.Native {
      def moduleDeps = Seq(snunit)
    }
    object `undertow-helloworld` extends CrossPlatform {
      object native extends CrossPlatformScalaModule with Common.Native {
        def moduleDeps = Seq(`snunit-undertow`)
      }
      object jvm extends CrossPlatformScalaModule with Common.Jvm {
        def ivyDeps = super.ivyDeps() ++ Agg(undertow)
      }
    }
    object `cask-helloworld` extends CrossPlatform {
      object jvm extends CrossPlatformScalaModule with Common.Jvm {
        def ivyDeps = super.ivyDeps() ++ Agg(
          ivy"com.lihaoyi::cask:${Versions.cask}"
        )
      }
      object native extends CrossPlatformScalaModule with Common.Native {
        def moduleDeps = Seq(`snunit-cask`)
      }
    }
    object `tapir-helloworld` extends Common.Native {
      override def moduleDeps = Seq(`snunit-tapir`)
    }
    // object `tapir-app` extends Common.Native {
    //   override def moduleDeps = Seq(`snunit-tapir-cats-effect`)
    // }
    // object `http4s-helloworld` extends Cross[Http4sHelloWorldModule](http4sVersions)
    // trait Http4sHelloWorldModule extends Common.Native with Cross.Module[String] {
    //   def http4sVersion = crossValue
    //   def moduleDeps = Seq(
    //     `snunit-http4s`(http4sVersion)
    //   )
    //   def ivyDeps = super.ivyDeps() ++ Agg(
    //     ivy"org.http4s::http4s-dsl::$http4sVersion"
    //   )
    // }
    // object `http4s-app` extends Common.Native {
    //   val http4sVersion = Versions.http4s1
    //   def moduleDeps = Seq(
    //     `snunit-http4s`(http4sVersion)
    //   )
    //   def ivyDeps = super.ivyDeps() ++ Agg(
    //     ivy"org.http4s::http4s-dsl::$http4sVersion"
    //   )
    // }
    // object `tapir-helloworld-cats-effect` extends Common.Native {
    //   def moduleDeps = Seq(
    //     `snunit-tapir-cats-effect`
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

// TODO: Update to latest Mill API
// object `snunit-mill-plugin` extends Common.Shared with Publish with BuildInfo {
//   def buildInfoMembers = Seq(
//     BuildInfo.Value("snunitVersion", publishVersion())
//   )
//   def buildInfoPackageName = "snunit.plugin.internal"
//   def artifactName = s"mill-snunit_mill${Versions.mill011.split('.').take(2).mkString(".")}"
//   def scalaVersion = mill.main.BuildInfo.scalaVersion
//   def compileIvyDeps = super.compileIvyDeps() ++ Agg(
//     ivy"com.lihaoyi::mill-scalanativelib:${mill.main.BuildInfo.millVersion}"
//   )

//   object test extends ScalaTests with TestModule.Utest with BuildInfo {
//     def ivyDeps = Agg(
//       ivy"com.lihaoyi::mill-testkit:${mill.main.BuildInfo.millVersion}",
//       ivy"com.lihaoyi::mill-scalanativelib:${mill.main.BuildInfo.millVersion}"
//     )
//     def forkEnv = Map("MILL_EXECUTABLE_PATH" -> millExecutable.assembly().path.toString)
//     def buildInfoMembers = Seq(
//       BuildInfo.Value("scalaNativeVersion", versions.Versions.scalaNative),
//       BuildInfo.Value("scalaVersion", versions.Versions.scala3)
//     )
//     def buildInfoPackageName = "snunit.plugin"
//     object millExecutable extends JavaModule {
//       def ivyDeps = Agg(
//         ivy"com.lihaoyi:mill-dist:${mill.main.BuildInfo.millVersion}"
//       )
//       def mainClass = Some("mill.runner.client.MillClientMain")
//       def resources = Task {
//         // make sure snunit is published
//         snunit.publishLocal()()

//         val p = Task.dest / "mill/local-test-overrides" / s"com.lihaoyi-${`snunit-mill-plugin`.artifactId()}"
//         os.write(p, `snunit-mill-plugin`.localClasspath().map(_.path).mkString("\n"), createFolders = true)
//         Seq(PathRef(Task.dest))
//       }
//     }
//   }
// }
