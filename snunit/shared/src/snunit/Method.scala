package snunit

class Method(val name: String) {
  override def toString(): String = s"Method($name)"
  override def equals(x: Any): Boolean = x match {
    case method: Method => method.name == name
    case _              => false
  }
  override def hashCode(): Int = name.hashCode()
}

object Method {
  final val GET = new Method("GET")
  final val HEAD = new Method("HEAD")
  final val POST = new Method("POST")
  final val PUT = new Method("PUT")
  final val DELETE = new Method("DELETE")
  final val CONNECT = new Method("CONNECT")
  final val OPTIONS = new Method("OPTIONS")
  final val TRACE = new Method("TRACE")
  final val PATCH = new Method("PATCH")
}
