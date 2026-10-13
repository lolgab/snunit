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
  * @param applications
  *   extra applications by name, other programs run by FreeUnit with a language module (see
  *   [[Application.executable]]), used by [[Pass.Application]]. The SNUnit application is always named `app`
  * @param upstreams
  *   named groups of servers, used by [[Pass.Upstream]]
  * @param certificates
  *   TLS certificate bundles (certificate chain and private key in PEM format) by name, used by [[Tls.certificates]]
  * @param observability
  *   metrics, traces and logs for OpenTelemetry, Prometheus, Jaeger and Perses, see [[Observability]]
  * @param sidecars
  *   extra processes (an OpenTelemetry collector...) started and supervised with FreeUnit, see [[Sidecar]]
  * @param shutdownTimeout
  *   time the running requests have to finish after SIGTERM or SIGINT, before the server is stopped without waiting
  */
final case class UnitConfig(
    listeners: Seq[Listener] = Seq(Listener()),
    application: Application = Application(),
    http: HttpSettings = HttpSettings(),
    routes: Map[String, Seq[RouteStep]] = Map.empty,
    applications: Map[String, Application] = Map.empty,
    upstreams: Map[String, Upstream] = Map.empty,
    certificates: Map[String, String] = Map.empty,
    accessLog: Option[AccessLog] = None,
    telemetry: Option[Telemetry] = None,
    /** Number of threads of the router that accept connections. */
    listenThreads: Option[Int] = None,
    observability: Option[Observability] = None,
    sidecars: Seq[Sidecar] = Nil,
    shutdownTimeout: FiniteDuration = 30.seconds
) {

  /** Listens on every address on `port` only. */
  def withPort(port: Int): UnitConfig = copy(listeners = Seq(Listener.port(port)))

  /** The observability configuration with the defaults the [[Stack]] implies (the metrics endpoint and the logs). */
  def resolvedObservability: Option[Observability] = observability.map(_.withStackDefaults)

  /** Adds the default [[Stack]], with [[Observability]] if there is none (with `serviceName`). */
  def withDefaultStack(serviceName: String): UnitConfig = observability match {
    case Some(o) if o.stack.isEmpty => copy(observability = Some(o.copy(stack = Some(Stack()))))
    case Some(_)                    => this
    case None                       => copy(observability = Some(Observability(serviceName, stack = Some(Stack()))))
  }

  /** Runs a fixed number of application processes. */
  def withProcesses(count: Int): UnitConfig = copy(application = application.withProcesses(count))

  /** This configuration with the [[Stack]] of [[Observability.stack]] added: its listeners and applications. */
  private def withStack(workDirectory: String): UnitConfig =
    observability.flatMap(o => o.stack.map(s => (o.withStackDefaults, s))) match {
      case Some((o, stack)) =>
        val data = stack.dataDirectory.getOrElse(s"$workDirectory/data")
        val added = stack.applications(workDirectory, data)
        require(
          added.keys.forall(!applications.contains(_)),
          s"the applications ${added.keys.toSeq.sorted.mkString(", ")} are used by the observability stack"
        )
        copy(observability = Some(o), listeners = listeners ++ stack.listeners, applications = applications ++ added)
      case None => this
    }

  /** The configuration files of the [[Stack]] to write before starting: path and content. Empty without a stack. */
  def stackFiles(workDirectory: String): Seq[(String, String)] =
    observability.flatMap(o => o.stack.map(s => (o.withStackDefaults, s))).toSeq.flatMap { (o, stack) =>
      val data = stack.dataDirectory.getOrElse(s"$workDirectory/data")
      stack.files(workDirectory, data, o).map((path, content) => s"$workDirectory/$path" -> content)
    }

  /** The directories of the data of the [[Stack]] to create before starting. */
  def stackDataDirectories(workDirectory: String): Seq[String] =
    observability
      .flatMap(_.stack)
      .toSeq
      .flatMap(s => s.dataDirectories(s.dataDirectory.getOrElse(s"$workDirectory/data")))

  /** The programs of the [[Stack]] that are not installed. */
  def missingStackPrograms: Seq[String] = observability.flatMap(_.stack).toSeq.flatMap(_.missingPrograms)

  /** The log files FreeUnit writes in `directory` when [[Logs]] are shipped: source name and path. */
  def logFiles(directory: String, workDirectory: String = "/tmp"): Seq[(String, String)] =
    withStack(workDirectory).logFilesOf(directory)

  private def logFilesOf(directory: String): Seq[(String, String)] =
    if (observability.exists(_.logs.nonEmpty))
      Seq("access" -> s"$directory/access.log", "unit" -> s"$directory/unit.log") ++
        ("app" +: applications.keys.toSeq.sorted).flatMap(name =>
          Seq(s"$name.stdout" -> s"$directory/$name.stdout.log", s"$name.stderr" -> s"$directory/$name.stderr.log")
        )
    else Nil

  /** The FreeUnit configuration running `executable` as the application.
    *
    * @param logDirectory
    *   where the logs are written when they are shipped, see [[logFiles]]
    * @param workDirectory
    *   where the files of the [[Stack]] are, see [[stackFiles]]
    */
  def toJson(executable: String, logDirectory: String = "/tmp", workDirectory: String = "/tmp"): String =
    withStack(workDirectory).render(executable, logDirectory)

  private def render(executable: String, logDirectory: String): String = {
    validate()
    Json.obj { o =>
      o.put("listeners", Json.Obj(listeners.map(l => l.address -> l.toJson)))
      o.put(
        "applications",
        Json.Obj(
          ("app" -> withLogs("app", effectiveApplication, logDirectory).toJson(executable)) +:
            applications.toSeq.sortBy(_._1).map((name, a) => name -> withLogs(name, a, logDirectory).toJson(executable))
        )
      )
      if (routes.nonEmpty)
        o.put(
          "routes",
          Json.Obj(routes.toSeq.sortBy(_._1).map((name, steps) => name -> Json.Arr(steps.map(_.toJson))))
        )
      if (upstreams.nonEmpty)
        o.put("upstreams", Json.Obj(upstreams.toSeq.sortBy(_._1).map((name, u) => name -> u.toJson)))
      o.put("access_log", effectiveAccessLog(logDirectory))(_.toJson)
      val settings = Json.obj { s =>
        s.put("listen_threads", listenThreads)(Json.int(_))
        s.put("telemetry", effectiveTelemetry)(_.toJson)
        val httpJson = http.toJson
        if (httpJson != Json.Obj(Nil)) s.put("http", httpJson)
      }
      if (settings != Json.Obj(Nil)) o.put("settings", settings)
    }.render
  }

  private def effectiveApplication: Application =
    observability.fold(application)(o => application.copy(environment = o.environment ++ application.environment))

  private def effectiveAccessLog(logDirectory: String): Option[AccessLog] =
    accessLog.orElse(
      observability
        .filter(_.accessLog)
        .map { o =>
          if (o.logs.nonEmpty) Observability.jsonAccessLog.copy(path = s"$logDirectory/access.log")
          else Observability.jsonAccessLog
        }
    )

  /** Redirects the output of the application to files that are shipped, unless it already goes to a file. */
  private def withLogs(name: String, application: Application, logDirectory: String): Application =
    if (observability.exists(_.logs.nonEmpty))
      application.copy(
        stdout = application.stdout.orElse(Some(s"$logDirectory/$name.stdout.log")),
        stderr = application.stderr.orElse(Some(s"$logDirectory/$name.stderr.log"))
      )
    else application

  private def effectiveTelemetry: Option[Telemetry] = telemetry.orElse(observability.flatMap(_.telemetry))

  private def validate(): Unit = {
    observability.foreach(_.validate())
    val sidecarNames = sidecars.map(_.name)
    require(
      sidecarNames.forall(_.matches("[A-Za-z0-9_.-]+")),
      "sidecar names can only contain letters, digits, _, . and -"
    )
    require(sidecarNames.distinct.size == sidecarNames.size, "duplicate sidecar names")
    require(!applications.contains("app"), "the application name 'app' is used by the SNUnit application")
    applications.foreach { (name, a) =>
      require(name.matches("[A-Za-z0-9_.-]+"), s"invalid application name '$name'")
      require(a.executable.nonEmpty, s"the application '$name' needs an executable")
    }
    require(sidecars.forall(_.command.nonEmpty), "a sidecar needs a command")
    require(listeners.nonEmpty, "at least one listener is needed")
    val duplicates = listeners.groupBy(_.address).collect { case (address, ls) if ls.size > 1 => address }
    require(duplicates.isEmpty, s"duplicate listener addresses: ${duplicates.mkString(", ")}")

    certificates.keys.foreach { name =>
      require(name.matches("[A-Za-z0-9_.-]+") && name != "." && name != "..", s"invalid certificate name '$name'")
    }

    def checkPass(where: String, pass: Pass): Unit = pass match {
      case Pass.Route(name) => require(routes.contains(name), s"$where passes to the undefined route '$name'")
      case Pass.Application(name) =>
        require(applications.contains(name), s"$where passes to the undefined application '$name'")
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
