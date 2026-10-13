package snunit.config

import java.nio.file.Paths

/** Metrics, traces, logs and dashboards running inside FreeUnit next to the application, already connected to each
  * other and to the application. They are builds of [[https://prometheus.io Prometheus]] (metrics),
  * [[https://www.jaegertracing.io Jaeger]] (traces), [[https://docs.victoriametrics.com/victorialogs/ VictoriaLogs]]
  * (logs) and [[https://perses.dev Perses]] (dashboards), all under the Apache-2.0 license. Prometheus and Jaeger are
  * CNCF graduated projects and Perses is a CNCF project too.
  *
  * They are modified builds (patched to be run by FreeUnit) published by the snunit project, not the official releases,
  * and are not endorsed or supported by the authors of those projects. Install them with `snunit stack install` (or
  * `snunit run --stack`). Set it in [[Observability.stack]]:
  *
  * {{{
  * UnitConfig(observability = Some(Observability("my-service", stack = Some(Stack()))))
  * }}}
  *
  *   - dashboards, on <http://localhost:3000>, has a dashboard "SNUnit on FreeUnit" with the request rate, the
  *     connections and the application processes
  *   - traces, on <http://localhost:16686>, shows the traces: FreeUnit sends them over OTLP/HTTP to
  *     `Observability.otlpEndpoint`, which must be on the `tracesOtlpPort` of the stack (the default one matches)
  *   - logs, on <http://localhost:9428/select/vmui>, has the logs: the launcher ships them to it (`Observability.logs`,
  *     enabled if not set)
  *   - metrics, on <http://localhost:9090>, scrapes the metrics endpoint of the launcher (`Observability.prometheus`,
  *     which is enabled with its defaults if not set)
  *
  * Ports (all fields of this class): 3000 dashboards, 9090 metrics, 16686 traces and 9428 logs go through FreeUnit and
  * are on `bindAddress`, and so is 4318, the OTLP/HTTP receiver of the traces program: it goes through FreeUnit too, to
  * the same program as the UI of Jaeger. The programs also open ports of their own on `127.0.0.1` behind FreeUnit
  * (`*InternalPort`, `tracesGrpcPort`): they are any free port by default (0). The application has its own port, 8080
  * by default. The launcher checks that the fixed ports are free before starting and prints where the programs are.
  *
  * The FreeUnit listeners of the programs are on `bindAddress`, the loopback interface by default. None of the programs
  * asks for a login, so don't expose them without your own route in front: change `bindAddress` only if you know what
  * is reachable from there.
  *
  * FreeUnit only speaks HTTP/1.1, so only the HTTP APIs of the programs go through it. The traces program also opens a
  * gRPC port and an internal HTTP port for itself; the dashboards and the logs have an internal HTTP port too.
  *
  * The licenses of the programs are in their directories, and in `stack/LICENSES.md` of the snunit repository.
  *
  * @param directory
  *   where `snunit stack install` put the programs. Default: the `SNUNIT_STACK_DIR` environment variable, or `stack` in
  *   the snunit cache directory (`~/.cache/snunit`)
  * @param dataDirectory
  *   where the programs keep their data. By default a temporary directory that is removed when the application stops:
  *   set it to keep the metrics, logs and traces
  */
final case class Stack(
    directory: Option[String] = None,
    dataDirectory: Option[String] = None,
    bindAddress: String = "127.0.0.1",
    dashboardsPort: Int = 3000,
    metricsPort: Int = 9090,
    tracesPort: Int = 16686,
    logsPort: Int = 9428,
    tracesOtlpPort: Int = 4318,
    dashboardsInternalPort: Int = 0,
    tracesInternalPort: Int = 0,
    tracesGrpcPort: Int = 0,
    logsInternalPort: Int = 0
) {

  /** The address and the name of every fixed port of the stack, that has to be free to start it. */
  private[snunit] def fixedPorts: Seq[(String, String, Int)] =
    Seq(
      ("dashboards", bindAddress, dashboardsPort),
      ("metrics", bindAddress, metricsPort),
      ("traces", bindAddress, tracesPort),
      ("logs", bindAddress, logsPort),
      ("traces OTLP/HTTP receiver", bindAddress, tracesOtlpPort),
      ("dashboards internal port", "127.0.0.1", dashboardsInternalPort),
      ("traces internal port", "127.0.0.1", tracesInternalPort),
      ("traces gRPC port", "127.0.0.1", tracesGrpcPort),
      ("logs internal port", "127.0.0.1", logsInternalPort)
    ).filter(_._3 > 0)

  /** Where to open the programs, for the message at startup. */
  private[snunit] def urls: Seq[(String, String)] =
    Seq(
      "dashboards" -> s"http://$localHost:$dashboardsPort",
      "metrics" -> s"http://$localHost:$metricsPort",
      "traces" -> s"http://$localHost:$tracesPort",
      "logs" -> s"http://$localHost:$logsPort/select/vmui"
    )

  /** The directory with the programs. */
  def resolvedDirectory: String = directory.orElse(sys.env.get("SNUNIT_STACK_DIR")).getOrElse {
    val base = sys.env
      .get("XDG_CACHE_HOME")
      .getOrElse(Paths.get(sys.props.getOrElse("user.home", "."), ".cache").toString)
    Paths.get(base, "snunit", "stack").toString
  }

  /** The programs that are not installed, so that the application can ask to install them. */
  def missingPrograms: Seq[String] = {
    val root = Paths.get(resolvedDirectory)
    Stack.programs.collect {
      case (name, relative) if !java.nio.file.Files.isRegularFile(root.resolve(relative)) => name
    }
  }

  /** Address to reach a program of the stack from the same machine. */
  private[config] def localHost: String =
    if (bindAddress == "0.0.0.0" || bindAddress == "::") "127.0.0.1" else bindAddress

  private[config] def listeners: Seq[Listener] =
    Seq(
      "dashboards" -> dashboardsPort,
      "metrics" -> metricsPort,
      "traces" -> tracesPort,
      "logs" -> logsPort,
      // The OTLP/HTTP receiver of the traces program, served by the same program as its UI.
      "traces" -> tracesOtlpPort
    ).map((name, port) => Listener(address = s"$bindAddress:$port", pass = Pass.Application(name)))

  /** The applications, to run with `workDirectory` for the configuration and `data` for the data. */
  private[config] def applications(workDirectory: String, data: String): Map[String, Application] = {
    val root = resolvedDirectory
    val programs = Stack.programs.toMap
    val conf = s"$workDirectory/stack"
    def app(name: String, arguments: Seq[String], environment: Map[String, String] = Map.empty) =
      name -> Application(
        executable = Some(s"$root/${programs(name)}"),
        arguments = arguments,
        environment = environment,
        // They keep local state and are not meant to run more than once.
        processes = Some(Processes.Fixed(1))
      )
    Map(
      app(
        "metrics",
        Seq(
          s"--config.file=$conf/metrics.yml",
          s"--storage.tsdb.path=$data/metrics",
          s"--web.listen-address=$bindAddress:$metricsPort"
        )
      ),
      app(
        "traces",
        Seq(s"--config=$conf/traces.yaml"),
        // The program would try to send its own spans to localhost:4317.
        Map("OTEL_TRACES_EXPORTER" -> "none", "OTEL_METRICS_EXPORTER" -> "none")
      ),
      app(
        "logs",
        Seq(
          s"-httpListenAddr=127.0.0.1:$logsInternalPort",
          s"-storageDataPath=$data/logs",
          "-retentionPeriod=7d"
        )
      ),
      app(
        "dashboards",
        Seq(s"--config=$conf/dashboards.yaml", s"--web.listen-address=127.0.0.1:$dashboardsInternalPort")
      )
    )
  }

  /** Configuration files to write before starting: path relative to `workDirectory` and content. */
  private[config] def files(
      workDirectory: String,
      data: String,
      observability: Observability
  ): Seq[(String, String)] = {
    val scrape = observability.prometheus.fold("127.0.0.1:9464")(p => s"${loopback(p.host)}:${p.port}")
    val provisioning = s"$workDirectory/stack/dashboards/provisioning"
    Seq(
      "stack/metrics.yml" ->
        s"""global:
           |  scrape_interval: 5s
           |scrape_configs:
           |  - job_name: snunit
           |    static_configs:
           |      - targets: ['$scrape']
           |""".stripMargin,
      "stack/traces.yaml" ->
        s"""service:
           |  extensions: [jaeger_storage, jaeger_query]
           |  pipelines:
           |    traces:
           |      receivers: [otlp]
           |      processors: [batch]
           |      exporters: [jaeger_storage_exporter]
           |  telemetry:
           |    metrics:
           |      level: none
           |    logs:
           |      level: info
           |extensions:
           |  jaeger_query:
           |    storage:
           |      traces: store
           |    http:
           |      endpoint: 127.0.0.1:$tracesInternalPort
           |    grpc:
           |      endpoint: 127.0.0.1:$tracesGrpcPort
           |  jaeger_storage:
           |    backends:
           |      store:
           |        badger:
           |          directories:
           |            keys: $data/traces/keys
           |            values: $data/traces/values
           |          ephemeral: false
           |          ttl:
           |            spans: 168h
           |receivers:
           |  otlp:
           |    protocols:
           |      http:
           |        endpoint: $bindAddress:$tracesOtlpPort
           |processors:
           |  batch:
           |exporters:
           |  jaeger_storage_exporter:
           |    trace_storage: store
           |""".stripMargin,
      "stack/dashboards.yaml" ->
        s"""security:
           |  readonly: false
           |  enable_auth: false
           |database:
           |  file:
           |    folder: $data/dashboards/db
           |    extension: json
           |provisioning:
           |  folders:
           |    - $provisioning
           |plugin:
           |  path: $data/dashboards/plugins
           |  archive_path: ${resolvedDirectory}/dashboards/plugins-archive
           |""".stripMargin,
      "stack/dashboards/provisioning/project.json" ->
        """{"kind":"Project","metadata":{"name":"snunit"},"spec":{"display":{"name":"SNUnit"}}}""",
      "stack/dashboards/provisioning/datasource.json" ->
        s"""{"kind":"GlobalDatasource","metadata":{"name":"prometheus"},"spec":{"default":true,"plugin":{"kind":"PrometheusDatasource","spec":{"proxy":{"kind":"HTTPProxy","spec":{"url":"http://$localHost:$metricsPort"}}}}}}""",
      "stack/dashboards/provisioning/dashboard.json" -> Stack.dashboard(localHost, tracesPort, logsPort)
    )
  }

  /** Directories to create before starting: the data of the programs. */
  private[config] def dataDirectories(data: String): Seq[String] =
    Seq("metrics", "traces", "logs", "dashboards/db", "dashboards/plugins").map(d => s"$data/$d")

  private def loopback(host: String): String = if (host == "0.0.0.0" || host == "::") "127.0.0.1" else host
}

object Stack {

  /** Program name and the path of its executable in the stack directory. */
  private[config] val programs: Seq[(String, String)] = Seq(
    "metrics" -> "metrics/snunit-metrics",
    "traces" -> "traces/snunit-traces",
    "logs" -> "logs/snunit-logs",
    "dashboards" -> "dashboards/snunit-dashboards"
  )

  private def query(promql: String, legend: String): String =
    s"""{"kind":"TimeSeriesQuery","spec":{"plugin":{"kind":"PrometheusTimeSeriesQuery","spec":{"query":"${promql
        .replace("\"", "\\\"")}","seriesNameFormat":"$legend"}}}}"""

  private def chart(name: String, queries: Seq[(String, String)]): String =
    s"""{"kind":"Panel","spec":{"display":{"name":"$name"},"plugin":{"kind":"TimeSeriesChart","spec":{"visual":{"areaOpacity":0.25,"lineWidth":2},"yAxis":{"min":0}}},"queries":[${queries
        .map(query)
        .mkString(",")}]}}"""

  private def stat(name: String, promql: String): String =
    s"""{"kind":"Panel","spec":{"display":{"name":"$name"},"plugin":{"kind":"StatChart","spec":{"calculation":"last","format":{"unit":"decimal"},"sparkline":{}}},"queries":[${query(
        promql,
        name
      )}]}}"""

  /** Request rate, connections and processes, and where to find the traces and the logs. */
  private[config] def dashboard(host: String, tracesPort: Int, logsPort: Int): String = {
    val links =
      s"""**Traces**: [open the traces](http://$host:$tracesPort) &nbsp;·&nbsp; **Logs**: [open the logs](http://$host:$logsPort/select/vmui)"""
    def item(x: Int, y: Int, w: Int, h: Int, panel: String) =
      s"""{"x":$x,"y":$y,"width":$w,"height":$h,"content":{"$$ref":"#/spec/panels/$panel"}}"""
    s"""{"kind":"Dashboard","metadata":{"name":"snunit","project":"snunit"},"spec":{"display":{"name":"SNUnit on FreeUnit"},""" +
      s""""duration":"15m","refreshInterval":"5s","panels":{""" +
      s""""total":${stat("Requests", "snunit_requests_total")},""" +
      s""""open":${stat("Open connections", "snunit_connections_active")},""" +
      s""""processes":${stat("Application processes", "sum(snunit_application_processes{state=\"running\"})")},""" +
      s""""requests":${chart("Requests per second", Seq("rate(snunit_requests_total[30s])" -> "req/s"))},""" +
      s""""connections":${chart(
          "Connections and processes",
          Seq(
            "snunit_connections_active" -> "active connections",
            "snunit_application_processes{state=\"running\"}" -> "{{application}} running"
          )
        )},""" +
      s""""links":{"kind":"Panel","spec":{"display":{"name":"Traces and logs"},"plugin":{"kind":"Markdown","spec":{"text":"$links"}}}}},""" +
      s""""layouts":[{"kind":"Grid","spec":{"display":{"title":"SNUnit"},"items":[""" +
      Seq(
        item(0, 0, 8, 4, "total"),
        item(8, 0, 8, 4, "open"),
        item(16, 0, 8, 4, "processes"),
        item(0, 4, 12, 9, "requests"),
        item(12, 4, 12, 9, "connections"),
        item(0, 13, 24, 3, "links")
      ).mkString(",") + "]}}]}}"
  }
}
