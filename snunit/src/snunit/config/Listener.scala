package snunit.config

import scala.concurrent.duration.FiniteDuration

/** How TLS session tickets are handled. */
enum Tickets {

  /** Tickets with a key generated and rotated by FreeUnit. */
  case Enabled

  /** No tickets. */
  case Disabled

  /** Tickets with this key (80 bytes encoded in Base64). */
  case Key(key: String)

  /** Tickets with these keys. The first is used to create tickets, all of them to accept them. */
  case Keys(keys: Seq[String])

  private[config] def toJson: Json = this match {
    case Enabled    => Json.Bool(true)
    case Disabled   => Json.Bool(false)
    case Key(key)   => Json.Str(key)
    case Keys(keys) => Json.strings(keys)
  }
}

final case class TlsSession(
    cacheSize: Option[Int] = None,
    timeout: Option[FiniteDuration] = None,
    tickets: Option[Tickets] = None
) {
  private[config] def toJson: Json = Json.obj { o =>
    o.put("cache_size", cacheSize)(Json.int(_))
    o.put("timeout", timeout)(Json.seconds)
    o.put("tickets", tickets)(_.toJson)
  }
}

/** TLS of a listener.
  *
  * @param certificates
  *   names of entries of [[UnitConfig.certificates]]
  * @param confCommands
  *   OpenSSL configuration commands, like `"MinProtocol" -> "TLSv1.2"`
  */
final case class Tls(
    certificates: Seq[String],
    confCommands: Map[String, String] = Map.empty,
    session: Option[TlsSession] = None
) {
  private[config] def toJson: Json = Json.obj { o =>
    o.putAll("certificate", certificates)
    o.putMap("conf_commands", confCommands)
    o.put("session", session)(_.toJson)
  }
}

/** Takes the client address and protocol from the headers of a trusted proxy (`X-Forwarded-For` style). */
final case class Forwarded(
    /** Addresses or networks of the trusted proxies. */
    source: Seq[String],
    /** Header with the client IP. */
    clientIp: Option[String] = None,
    /** Header with the protocol, like `X-Forwarded-Proto`. */
    protocol: Option[String] = None,
    recursive: Option[Boolean] = None
) {
  private[config] def toJson: Json = Json.obj { o =>
    o.put("client_ip", clientIp)(Json.Str(_))
    o.put("protocol", protocol)(Json.Str(_))
    o.putAll("source", source)
    o.put("recursive", recursive)(Json.Bool(_))
  }
}

/** Takes the client address from a header sent by a trusted proxy. */
final case class ClientIp(header: String, source: Seq[String], recursive: Option[Boolean] = None) {
  private[config] def toJson: Json = Json.obj { o =>
    o.put("header", Json.Str(header))
    o.putAll("source", source)
    o.put("recursive", recursive)(Json.Bool(_))
  }
}

/** A socket the server listens on.
  *
  * @param address
  *   `*:8080` (any address), `127.0.0.1:8080`, `[::1]:8080` or `unix:/path/to/socket`
  */
final case class Listener(
    address: String = "*:8080",
    pass: Pass = Pass.App,
    backlog: Option[Int] = None,
    tls: Option[Tls] = None,
    forwarded: Option[Forwarded] = None,
    clientIp: Option[ClientIp] = None
) {
  private[config] def toJson: Json = Json.obj { o =>
    o.put("pass", Json.Str(pass.target))
    o.put("backlog", backlog)(Json.int(_))
    o.put("tls", tls)(_.toJson)
    o.put("forwarded", forwarded)(_.toJson)
    o.put("client_ip", clientIp)(_.toJson)
  }
}

object Listener {

  /** Listens on every address on `port`. */
  def port(port: Int): Listener = Listener(s"*:$port")

  /** Listens on localhost only. */
  def localhost(port: Int): Listener = Listener(s"127.0.0.1:$port")

  def unixSocket(path: String): Listener = Listener(s"unix:$path")
}
