package java.util.logging

class Level
object Level {
  val WARNING: Level = new Level
}
class Logger {
  def setLevel(level: Level): Unit = ()
}
object Logger {
  def getLogger(s: String): Logger = new Logger
}
