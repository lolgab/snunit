package snunit.plugin

sealed trait BuildTool
object BuildTool {
  case object Sbt extends BuildTool
  case object Mill extends BuildTool
}
