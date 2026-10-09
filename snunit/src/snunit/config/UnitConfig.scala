package snunit.config

import scala.concurrent.duration.*

/** Configuration of the standalone SNUnit executable and of the FreeUnit server it starts.
  *
  * It is used when the executable is started directly: it makes the executable start `unitd` and run itself as its
  * application. When the executable is started by FreeUnit, or by something else configuring FreeUnit, it is ignored.
  *
  * Everything that is not set keeps the FreeUnit default.
  *
  * {{{
  * SyncServerBuilder
  *   .setConfig(
  *     UnitConfig(
  *       listeners = Seq(Listener.port(9000)),
  *       application = Application().withProcesses(4),
  *       http = HttpSettings(maxBodySize = Some(1024 * 1024))
  *     )
  *   )
  *   .setRequestHandler(handler)
  *   .build()
  *   .listen()
  * }}}
  *
  * @param listeners
  *   sockets to listen on, by default the port 8080 on every address
  * @param application
  *   options of the application processes
  * @param http
  *   HTTP settings
  * @param routes
  *   named routes, used by [[Pass.Route]]. Listeners can pass requests to a route, for example to serve static files or
  *   to proxy some paths, instead of passing everything to the application
  * @param upstreams
  *   named groups of servers, used by [[Pass.Upstream]]
  * @param certificates
  *   TLS certificate bundles (certificate chain and private key in PEM format) by name, used by [[Tls.certificates]]
  * @param shutdownTimeout
  *   time the running requests have to finish after SIGTERM or SIGINT, before the server is stopped without waiting
  */
final case class UnitConfig(
    listeners: Seq[Listener] = Seq(Listener()),
    application: Application = Application(),
    http: HttpSettings = HttpSettings(),
    routes: Map[String, Seq[RouteStep]] = Map.empty,
    upstreams: Map[String, Upstream] = Map.empty,
    certificates: Map[String, String] = Map.empty,
    accessLog: Option[AccessLog] = None,
    telemetry: Option[Telemetry] = None,
    /** Number of threads of the router that accept connections. */
    listenThreads: Option[Int] = None,
    shutdownTimeout: FiniteDuration = 30.seconds
) {

  /** Listens on every address on `port` only. */
  def withPort(port: Int): UnitConfig = copy(listeners = Seq(Listener.port(port)))

  /** Runs a fixed number of application processes. */
  def withProcesses(count: Int): UnitConfig = copy(application = application.withProcesses(count))

  /** The FreeUnit configuration running `executable` as the application. */
  def toJson(executable: String): String = {
    validate()
    Json.obj { o =>
      o.put("listeners", Json.Obj(listeners.map(l => l.address -> l.toJson)))
      o.put("applications", Json.obj(_.put("app", application.toJson(executable))))
      if (routes.nonEmpty)
        o.put(
          "routes",
          Json.Obj(routes.toSeq.sortBy(_._1).map((name, steps) => name -> Json.Arr(steps.map(_.toJson))))
        )
      if (upstreams.nonEmpty)
        o.put("upstreams", Json.Obj(upstreams.toSeq.sortBy(_._1).map((name, u) => name -> u.toJson)))
      o.put("access_log", accessLog)(_.toJson)
      val settings = Json.obj { s =>
        s.put("listen_threads", listenThreads)(Json.int(_))
        s.put("telemetry", telemetry)(_.toJson)
        val httpJson = http.toJson
        if (httpJson != Json.Obj(Nil)) s.put("http", httpJson)
      }
      if (settings != Json.Obj(Nil)) o.put("settings", settings)
    }.render
  }

  private def validate(): Unit = {
    require(listeners.nonEmpty, "at least one listener is needed")
    val duplicates = listeners.groupBy(_.address).collect { case (address, ls) if ls.size > 1 => address }
    require(duplicates.isEmpty, s"duplicate listener addresses: ${duplicates.mkString(", ")}")

    certificates.keys.foreach { name =>
      require(name.matches("[A-Za-z0-9_.-]+") && name != "." && name != "..", s"invalid certificate name '$name'")
    }

    def checkPass(where: String, pass: Pass): Unit = pass match {
      case Pass.Route(name)    => require(routes.contains(name), s"$where passes to the undefined route '$name'")
      case Pass.Upstream(name) => require(upstreams.contains(name), s"$where passes to the undefined upstream '$name'")
      case Pass.App            => ()
    }
    def checkAction(where: String, action: Action): Unit = action match {
      case Action.PassTo(pass, _, _) => checkPass(where, pass)
      case share: Action.Share       => share.fallback.foreach(checkAction(where, _))
      case _                         => ()
    }
    listeners.foreach { listener =>
      checkPass(s"the listener ${listener.address}", listener.pass)
      listener.tls.foreach(_.certificates.foreach { name =>
        require(certificates.contains(name), s"the listener ${listener.address} uses the undefined certificate '$name'")
      })
    }
    for ((name, steps) <- routes; step <- steps) checkAction(s"the route '$name'", step.action)
  }
}
