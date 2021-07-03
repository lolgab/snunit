import xerial.sbt.Sonatype._
import sjsonnew._
import sjsonnew.BasicJsonProtocol._
import sjsonnew.shaded.scalajson.ast.unsafe._
import sjsonnew.support.scalajson.unsafe._

def getFromMillJson(taskName: String): JValue = {
  val json = Parser.parseFromFile(file(s"../out/snunit-plugins-shared/2.12.14/$taskName/meta.json")).get
  json match {
    case JObject(fields) =>
      fields.collectFirst {
        case JField("value", value) => value
      }.get
    case _ => throw new Exception("Not an object")
  }
}

val snunitVersion = getFromMillJson("publishVersion").asInstanceOf[JString].value

lazy val snunitSbtPlugin = project.in(file("."))
  .settings(
    name := "snunit-sbt-plugin",
    version := snunitVersion,
    sbtPlugin := true,
    scalaVersion := "2.12.14",
    organization := "com.github.lolgab",
    addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.0"),
    libraryDependencies ++= Seq(
      "com.github.lolgab" %% "snunit-plugins-shared" % snunitVersion
    ),
    publishTo := sonatypePublishToBundle.value,
    publishMavenStyle := true,
    sonatypeProfileName := "com.github.lolgab",
    licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT")),
    sonatypeProjectHosting := Some(GitHubHosting("lolgab", "snunit", "lorenzolespaul@gmail.com")),
    developers := List(
      Developer(
        id = "lolgab",
        name = "Lorenzo Gabriele",
        email = "lorenzolespaul@gmail.com",
        url = url("https://github.com/lolgab")
      )
    ),
    credentials += Credentials(
      "Sonatype Nexus Repository Manager",
      "oss.sonatype.org",
      sys.env.getOrElse("SONATYPE_USER", "username"),
      sys.env.getOrElse("SONATYPE_PASSWORD", "password")
    ),
  )

