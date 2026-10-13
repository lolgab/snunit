import scala.sys.process.*

scalaVersion := "3.9.0"
enablePlugins(ScalaNativePlugin, SNUnitPlugin)

libraryDependencies += "com.github.lolgab" % "snunit_native0.5_3" % sys.props.getOrElse("snunit.version", "0.11.0")

// Starts the bundled executable and checks that it serves requests: it must carry its own unitd.
val checkServes = taskKey[Unit]("")
checkServes := {
  val exe = snunitBundle.value
  val log = java.nio.file.Files.createTempFile("snunit", ".log").toFile
  val proc = Process(Seq(exe.getAbsolutePath)).run(ProcessLogger(log))
  try {
    val deadline = System.currentTimeMillis() + 15000
    var body = ""
    while (body.isEmpty && System.currentTimeMillis() < deadline) {
      body = scala.util.Try(scala.io.Source.fromURL("http://localhost:8080/").mkString.trim).getOrElse("")
      if (body.isEmpty) Thread.sleep(300)
    }
    assert(body == "Hello world!", s"unexpected response '$body'")
  } finally proc.destroy()
}
