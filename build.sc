import mill._, mill.scalalib._, mill.scalanativelib._, mill.scalanativelib.api._
import $ivy.`com.lihaoyi::mill-contrib-bloop:0.7.4`

trait Common extends ScalaNativeModule {
  def organization = "com.github.lolgab"
  def name = "snunit"

  def scalaVersion = "2.11.12"
  def scalaNativeVersion = "0.4.0-M2"

  def nativeLinkingOptions = T {
    Array(
      "/usr/local/lib/libunit.a"
    ) ++ super.nativeLinkingOptions()
  }

  def baseTestConfig(binary: os.Path, numProcesses: Int = 1, appName: String = "test_app", port: Int = 8081) = ujson.Obj(
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

  def testConfig = {}

  def deployTestApp() = T.command {
    val binary = nativeLink()

    import scala.sys.process._
    def doCurl(data: String) = s"curl -X PUT --data-binary '$data' --unix-socket /usr/local/var/run/unit/control.sock http://localhost/config".!!
    
    doCurl(baseTestConfig(binary = binary, numProcesses = 0).toString)
    doCurl(baseTestConfig(binary = binary, numProcesses = 1).toString)
  }

  object test extends Tests {
    def testFrameworks = Seq.empty[String]

    def nativeLinkStubs = true
  }
}

object snunit extends Common {
  object endpoints extends Common {
    def moduleDeps = Seq(snunit)

    def ivyDeps = T {
      super.ivyDeps() ++ Agg(
        ivy"org.julienrf::endpoints-algebra::0.15.0+96-ef74346f+20200620-1500",
        ivy"com.lihaoyi::upickle::1.1.0"
      )
    }
  }

  object autowire extends Common {
    def moduleDeps = Seq(snunit)

    def releaseMode = ReleaseMode.ReleaseFast

    // def scalacOptions = T { super.scalacOptions() ++ Seq("-Ymacro-debug-verbose")}

    def ivyDeps = T {
      super.ivyDeps() ++ Agg(
        ivy"com.lihaoyi::autowire::0.2.7",
        ivy"com.lihaoyi::upickle::1.1.0"
      )
    }
  }
}

object snclient extends ScalaModule {
  def scalaVersion = "2.13.3"

  def ivyDeps = Agg(ivy"com.lihaoyi::autowire::0.2.7", ivy"com.lihaoyi::upickle::1.1.0")
}
