package snunit.config

/** A destination of a listener or of a route action. */
enum Pass {

  /** The SNUnit application. */
  case App

  /** The route with this name of [[UnitConfig.routes]]. */
  case Route(name: String)

  /** The upstream with this name of [[UnitConfig.upstreams]]. */
  case Upstream(name: String)

  private[config] def target: String = this match {
    case Pass.App            => "applications/app"
    case Pass.Route(name)    => s"routes/$name"
    case Pass.Upstream(name) => s"upstreams/$name"
  }
}

/** A set of patterns that must all match: name to the patterns accepted for it (any of them). */
type PatternSet = Map[String, Seq[String]]

/** The conditions of a route step. All the set conditions must be met. Patterns can use `*` wildcards, `!` negation and
  * the other FreeUnit pattern features. Unset conditions always match.
  */
final case class Match(
    method: Seq[String] = Nil,
    scheme: Option[String] = None,
    host: Seq[String] = Nil,
    /** Source IP addresses or networks. */
    source: Seq[String] = Nil,
    /** Destination IP addresses or networks, with an optional port. */
    destination: Seq[String] = Nil,
    uri: Seq[String] = Nil,
    query: Seq[String] = Nil,
    /** Query string arguments. With more than one set, it is enough that one matches. */
    arguments: Seq[PatternSet] = Nil,
    headers: Seq[PatternSet] = Nil,
    cookies: Seq[PatternSet] = Nil,
    /** A condition expression, like `$request_uri == '/'`. */
    condition: Option[String] = None
) {
  private def sets(patternSets: Seq[PatternSet]): Json = {
    val objects =
      patternSets.map(set => Json.Obj(set.toSeq.sortBy(_._1).map((name, patterns) => name -> Json.oneOrMany(patterns))))
    objects match {
      case Seq(single) => single
      case _           => Json.Arr(objects)
    }
  }

  private[config] def toJson: Json = Json.obj { o =>
    o.putAll("method", method)
    o.put("scheme", scheme)(Json.Str(_))
    o.putAll("host", host)
    o.putAll("source", source)
    o.putAll("destination", destination)
    o.putAll("uri", uri)
    o.putAll("query", query)
    if (arguments.nonEmpty) o.put("arguments", sets(arguments))
    if (headers.nonEmpty) o.put("headers", sets(headers))
    if (cookies.nonEmpty) o.put("cookies", sets(cookies))
    o.put("if", condition)(Json.Str(_))
  }
}

/** What a route step does with the matching requests. */
sealed trait Action {

  /** Replaces the URI of the request before the action. */
  def rewrite: Option[String]

  /** Response header fields to set before the action. */
  def responseHeaders: Map[String, String]

  private[config] def toJson: Json = Json.obj { o =>
    kind(o)
    o.put("rewrite", rewrite)(Json.Str(_))
    o.putMap("response_headers", responseHeaders)
  }

  protected def kind(o: Json.ObjBuilder): Unit
}

object Action {

  /** Passes the request to the application, another route or an upstream. */
  final case class PassTo(
      pass: Pass = Pass.App,
      rewrite: Option[String] = None,
      responseHeaders: Map[String, String] = Map.empty
  ) extends Action {
    protected def kind(o: Json.ObjBuilder): Unit = o.put("pass", Json.Str(pass.target))
  }

  /** Proxies the request to an HTTP server, like `127.0.0.1:8081` or `unix:/tmp/server.sock`. */
  final case class Proxy(
      address: String,
      rewrite: Option[String] = None,
      responseHeaders: Map[String, String] = Map.empty
  ) extends Action {
    protected def kind(o: Json.ObjBuilder): Unit = o.put("proxy", Json.Str(address))
  }

  /** Responds with a status code, and optionally a redirect location. */
  final case class Return(
      status: Int,
      location: Option[String] = None,
      rewrite: Option[String] = None,
      responseHeaders: Map[String, String] = Map.empty
  ) extends Action {
    protected def kind(o: Json.ObjBuilder): Unit = {
      o.put("return", Json.int(status))
      o.put("location", location)(Json.Str(_))
    }
  }

  /** Serves static files. FreeUnit tries the `paths` in order. */
  final case class Share(
      paths: Seq[String],
      /** File served when a path is a directory. Default: `index.html`. */
      index: Option[String] = None,
      /** MIME type patterns of the files that are served. */
      types: Seq[String] = Nil,
      /** What to do when no file is served. */
      fallback: Option[Action] = None,
      chroot: Option[String] = None,
      followSymlinks: Option[Boolean] = None,
      traverseMounts: Option[Boolean] = None,
      rewrite: Option[String] = None,
      responseHeaders: Map[String, String] = Map.empty
  ) extends Action {
    protected def kind(o: Json.ObjBuilder): Unit = {
      o.putAll("share", paths)
      o.put("index", index)(Json.Str(_))
      o.putAll("types", types)
      o.put("fallback", fallback)(_.toJson)
      o.put("chroot", chroot)(Json.Str(_))
      o.put("follow_symlinks", followSymlinks)(Json.Bool(_))
      o.put("traverse_mounts", traverseMounts)(Json.Bool(_))
    }
  }
}

/** A step of a route: the first step of the route whose `matching` conditions are met runs its `action`. */
final case class RouteStep(action: Action, matching: Match = Match()) {
  private[config] def toJson: Json = Json.obj { o =>
    val conditions = matching.toJson
    if (conditions != Json.Obj(Nil)) o.put("match", conditions)
    o.put("action", action.toJson)
  }
}

/** A group of servers requests are balanced between. */
final case class Upstream(servers: Seq[Upstream.Server]) {
  private[config] def toJson: Json = Json.obj { o =>
    o.put(
      "servers",
      Json.Obj(
        servers.map(server => server.address -> Json.obj(_.put("weight", server.weight)(w => Json.Num(w.toString))))
      )
    )
  }
}

object Upstream {

  /** A server at `address` (like `127.0.0.1:8081`). Requests are distributed proportionally to the `weight`. */
  final case class Server(address: String, weight: Option[Double] = None)
}
