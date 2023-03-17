package snunit

import scala.annotation.targetName

type Version = String

object Version {
  def apply(value: String): Method = value

  final val `HTTP/1.1`: Method = "HTTP/1.1"
  final val `HTTP/1.0`: Method = "HTTP/1.0"
}
