package io.undertow.server.handlers.form

final class FormData extends java.lang.Iterable[String] {
  def get(key: String): java.util.Deque[FormData.FormValue] = ???
  def iterator(): java.util.Iterator[String] = ???
}
object FormData {
  final class FormValue {
    def isFile(): Boolean = ???
    def getHeaders() = ???
    def getFileName() = ???
    def getPath() = ???
    def getValue() = ???
  }
}
