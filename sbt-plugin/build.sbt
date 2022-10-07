import xerial.sbt.Sonatype._
import sjsonnew._
import sjsonnew.BasicJsonProtocol._
import sjsonnew.shaded.scalajson.ast.unsafe._
import sjsonnew.support.scalajson.unsafe._
import scala.sys.process._

val snunitVersion = Def.setting {
  val snunitDir = baseDirectory.value / ".."
  val versionString =
    Process(
      Seq("./mill", "--disable-ticker", "show", s"snunit-plugins-shared[${Versions.scala212}].publishVersion"),
      cwd = snunitDir
    ).!!
  val JString(version) = Parser.parseFromString(versionString).get
  version
}

lazy val snunitSbtPlugin = project
  .in(file("."))
  .settings(
    name := "snunit-sbt-plugin",
    version := snunitVersion.value,
    sbtPlugin := true,
    scalaVersion := Versions.scala212,
    organization := "com.github.lolgab",
    addSbtPlugin("org.scala-native" % "sbt-scala-native" % Versions.scalaNative),
    libraryDependencies ++= Seq(
      "com.github.lolgab" %% "snunit-plugins-shared" % snunitVersion.value
    ),
    publishTo := sonatypePublishToBundle.value,
    publishMavenStyle := true,
    sonatypeProfileName := "com.github.lolgab",
    licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
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
    )
  )
