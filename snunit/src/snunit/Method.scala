package snunit

class Method(val name: String)

object Method {
  case object GET extends Method("GET")
  case object HEAD extends Method("HEAD")
  case object POST extends Method("POST")
  case object PUT extends Method("PUT")
  case object DELETE extends Method("DELETE")
  case object CONNECT extends Method("CONNECT")
  case object OPTIONS extends Method("OPTIONS")
  case object TRACE extends Method("TRACE")
  case object PATCH extends Method("PATCH")
}
