// sbt plugin (1.x and 2.x) that links `libunit` and embeds `unitd` into Scala Native executables.
// The logic shared with the `snunit` command line tool lives in ../snunit-build-core.
organization := "com.github.lolgab"
name := "sbt-snunit"
version := "0.1.0-SNAPSHOT"

enablePlugins(SbtPlugin)

val scala212 = "2.12.20"
val scala3 = "3.8.4" // what sbt 2 uses

crossScalaVersions := Seq(scala212, scala3)

pluginCrossBuild / sbtVersion := {
  scalaBinaryVersion.value match {
    case "2.12" => "1.13.0"
    case _      => "2.0.10"
  }
}

Compile / unmanagedSourceDirectories += baseDirectory.value / ".." / "snunit-build-core"
Compile / unmanagedSourceDirectories += {
  baseDirectory.value / "src" / "main" / (if (scalaBinaryVersion.value == "2.12") "scala-sbt-1.0" else "scala-sbt-2.0")
}

addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")

scriptedLaunchOpts ++= Seq("-Xmx1024M", s"-Dplugin.version=${version.value}")
scriptedBufferLog := false
