package snunit.plugin

trait Logger {
  def info(s: String): Unit
  def warn(s: String): Unit
  def error(s: String): Unit
}
object Logger {
  def stderr = new Logger {
    def info(s: String): Unit = System.err.println(s"[info] $s")
    def warn(s: String): Unit = System.err.println(s"[warn] $s")
    def error(s: String): Unit = System.err.println(s"[error] $s")
  }
}
