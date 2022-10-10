enablePlugins(ScalaNativePlugin)
enablePlugins(SNUnitPlugin)

scalaVersion := "3.2.0"
snunitPort := 8085
snunitCurlCommand := Seq("sudo", "curl")
libraryDependencies += "com.github.lolgab" %%% "snunit" % "0.1.0"

lazy val doCallToServer = taskKey[Unit]("test that server works")
doCallToServer := {
  import sys.process._
  val port = snunitPort.value
  val response = s"curl -sL http://127.0.0.1:$port".!!
  require(response == "Hello world\n")
}
