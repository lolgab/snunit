package snunit

import scala.annotation.targetName

type Method = String

object Method {
  inline def apply(value: String): Method = value

  final val GET: Method = "GET"
  final val HEAD: Method = "HEAD"
  final val POST: Method = "POST"
  final val PUT: Method = "PUT"
  final val DELETE: Method = "DELETE"
  final val CONNECT: Method = "CONNECT"
  final val OPTIONS: Method = "OPTIONS"
  final val TRACE: Method = "TRACE"
  final val PATCH: Method = "PATCH"
}
