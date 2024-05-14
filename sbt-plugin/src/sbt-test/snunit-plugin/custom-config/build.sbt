enablePlugins(ScalaNativePlugin)
enablePlugins(SNUnitPlugin)

scalaVersion := "3.2.0"
snunitConfig := """{
  "listeners": {
    "*:8086": {
      "pass": "routes"
    }
  },
  "routes": [
    {
      "action": {
        "pass": "applications/my_app"
      }
    }
  ],
  "applications": {
    "my_app": {
      "processes": {
        "max": 10,
        "spare": 2,
        "idle_timeout": 1
      },
      "type": "external",
      "executable": "$SNUNIT_BINARY",
      "limits": {
        "timeout": 1,
        "requests": 1000
      }
    }
  }
}"""
snunitCurlCommand := Seq("sudo", "curl")
snunitAppName := "my_app"
libraryDependencies += "com.github.lolgab" %%% "snunit" % snunitVersion

lazy val doCallToServer = taskKey[Unit]("test that server works")
doCallToServer := {
  import sys.process._
  val port = snunitPort.value
  val response = s"curl -sL http://127.0.0.1:8086".!!
  require(response == "Hello world\n")
}
