package snunit

type Version = String

object Version {
  inline def apply(value: String): Version = value

  final val `HTTP/1.1`: Version = "HTTP/1.1"
  final val `HTTP/1.0`: Version = "HTTP/1.0"
}
