package snunit.config

import utest.*
import scala.concurrent.duration.*

object UnitConfigTests extends TestSuite {
  val tests = Tests {
    test("default") {
      UnitConfig().toJson("/bin/app") ==>
        """{"listeners":{"*:8080":{"pass":"applications/app"}},"applications":{"app":{"type":"external","executable":"/bin/app"}}}"""
    }
    test("portAndProcesses") {
      UnitConfig().withPort(9000).withProcesses(4).toJson("/bin/app") ==>
        """{"listeners":{"*:9000":{"pass":"applications/app"}},"applications":{"app":{"type":"external","executable":"/bin/app","processes":4}}}"""
    }
    test("executableIsEscaped") {
      UnitConfig().toJson("/tmp/a \"b\"\\c\n").contains("\"executable\":\"/tmp/a \\\"b\\\"\\\\c\\n\"") ==> true
    }
    test("httpSettings") {
      val config = UnitConfig(
        http = HttpSettings(
          maxBodySize = Some(1024),
          idleTimeout = Some(2.minutes),
          discardUnsafeFields = Some(false),
          webSocket = Some(WebSocketSettings(maxFrameSize = Some(4096))),
          staticMimeTypes = Map("text/x-foo" -> Seq(".foo"))
        ),
        listenThreads = Some(2)
      )
      config.toJson("/bin/app") ==>
        """{"listeners":{"*:8080":{"pass":"applications/app"}},"applications":{"app":{"type":"external","executable":"/bin/app"}},"settings":{"listen_threads":2,"http":{"idle_timeout":120,"max_body_size":1024,"discard_unsafe_fields":false,"websocket":{"max_frame_size":4096},"static":{"mime_types":{"text/x-foo":".foo"}}}}}"""
    }
    test("application") {
      val application = Application(
        arguments = Seq("--a"),
        environment = Map("B" -> "2", "A" -> "1"),
        user = Some("nobody"),
        processes = Some(Processes.Dynamic(max = 8, spare = 2, idleTimeout = Some(10.seconds))),
        limits = Some(Limits(requests = Some(100), timeout = Some(5.seconds))),
        isolation = Some(Isolation(namespaces = Some(Namespaces(pid = Some(true))), uidMap = Seq(IdMap(0, 1000, 1))))
      )
      application.toJson("/bin/app").render ==>
        """{"type":"external","executable":"/bin/app","arguments":["--a"],"environment":{"A":"1","B":"2"},"user":"nobody","processes":{"max":8,"spare":2,"idle_timeout":10},"limits":{"requests":100,"timeout":5},"isolation":{"namespaces":{"pid":true},"uidmap":[{"container":0,"host":1000,"size":1}]}}"""
    }
    test("routes") {
      val config = UnitConfig(
        listeners = Seq(Listener(pass = Pass.Route("main"))),
        routes = Map(
          "main" -> Seq(
            RouteStep(
              Action.Share(Seq("/www/$uri"), fallback = Some(Action.Return(404))),
              Match(uri = Seq("/static/*"), method = Seq("GET", "HEAD"), headers = Seq(Map("Host" -> Seq("a.com"))))
            ),
            RouteStep(Action.Proxy("127.0.0.1:9000"), Match(uri = Seq("/api/*"))),
            RouteStep(Action.PassTo())
          )
        )
      )
      config.toJson("/bin/app") ==>
        """{"listeners":{"*:8080":{"pass":"routes/main"}},"applications":{"app":{"type":"external","executable":"/bin/app"}},"routes":{"main":[{"match":{"method":["GET","HEAD"],"uri":"/static/*","headers":{"Host":"a.com"}},"action":{"share":"/www/$uri","fallback":{"return":404}}},{"match":{"uri":"/api/*"},"action":{"proxy":"127.0.0.1:9000"}},{"action":{"pass":"applications/app"}}]}}"""
    }
    test("tls") {
      val config = UnitConfig(
        listeners =
          Seq(Listener.port(8443).copy(tls = Some(Tls(Seq("cert"), confCommands = Map("MinProtocol" -> "TLSv1.2"))))),
        certificates = Map("cert" -> "PEM")
      )
      config.toJson("/bin/app") ==>
        """{"listeners":{"*:8443":{"pass":"applications/app","tls":{"certificate":"cert","conf_commands":{"MinProtocol":"TLSv1.2"}}}},"applications":{"app":{"type":"external","executable":"/bin/app"}}}"""
    }
    test("upstreams") {
      val config = UnitConfig(
        listeners = Seq(Listener(pass = Pass.Upstream("pool"))),
        upstreams =
          Map("pool" -> Upstream(Seq(Upstream.Server("127.0.0.1:1", Some(2)), Upstream.Server("127.0.0.1:2"))))
      )
      config
        .toJson("/bin/app")
        .contains(""""upstreams":{"pool":{"servers":{"127.0.0.1:1":{"weight":2.0},"127.0.0.1:2":{}}}}""") ==> true
    }
    test("accessLogAndTelemetry") {
      val config = UnitConfig(
        accessLog = Some(AccessLog("/dev/stdout", Some(AccessLog.Format.Text("$status")))),
        telemetry = Some(Telemetry("http://localhost:4318", samplingRatio = Some(0.5)))
      )
      val json = config.toJson("/bin/app")
      json.contains(""""access_log":{"path":"/dev/stdout","format":"$status"}""") ==> true
      json.contains(""""telemetry":{"endpoint":"http://localhost:4318","sampling_ratio":0.5}""") ==> true
    }
    test("observability") {
      val config = UnitConfig(
        observability = Some(
          Observability(
            "svc",
            serviceVersion = Some("1.0"),
            resourceAttributes = Map("deployment.environment.name" -> "a b,c"),
            traces = Some(Traces(samplingRatio = Some(0.5)))
          )
        ),
        sidecars = Seq(Sidecar.otelCollector("c.yaml"))
      )
      val json = config.toJson("/bin/app")
      json.contains(
        """"telemetry":{"endpoint":"http://localhost:4318/v1/traces","protocol":"http","sampling_ratio":0.5}"""
      ) ==> true
      json.contains(""""access_log":{"path":"/dev/stdout","format":{""") ==> true
      json.contains(""""OTEL_SERVICE_NAME":"svc"""") ==> true
      json.contains(
        """"OTEL_RESOURCE_ATTRIBUTES":"service.version=1.0,deployment.environment.name=a%20b%2Cc""""
      ) ==> true
      json.contains(""""OTEL_TRACES_SAMPLER":"parentbased_traceidratio"""") ==> true
    }
    test("observabilityStackDisablesLogsAndMetricsExporters") {
      val json = UnitConfig(observability = Some(Observability("svc", stack = Some(Stack())))).toJson("/bin/app")
      json.contains(""""OTEL_LOGS_EXPORTER":"none"""") ==> true
      json.contains(""""OTEL_METRICS_EXPORTER":"none"""") ==> true
    }
    test("observabilityOwnCollectorKeepsExporters") {
      val json = UnitConfig(
        observability = Some(Observability("svc", otlpEndpoint = "http://collector:4318", stack = Some(Stack())))
      ).toJson("/bin/app")
      json.contains("OTEL_LOGS_EXPORTER") ==> false
      val noStack = UnitConfig(observability = Some(Observability("svc"))).toJson("/bin/app")
      noStack.contains("OTEL_LOGS_EXPORTER") ==> false
    }
    test("observabilityExplicitWins") {
      val config = UnitConfig(
        observability = Some(Observability("svc")),
        application = Application(environment = Map("OTEL_SERVICE_NAME" -> "mine")),
        accessLog = Some(AccessLog("/tmp/log")),
        telemetry = Some(Telemetry("http://x"))
      )
      val json = config.toJson("/bin/app")
      json.contains(""""OTEL_SERVICE_NAME":"mine"""") ==> true
      json.contains(""""access_log":{"path":"/tmp/log"}""") ==> true
      json.contains(""""telemetry":{"endpoint":"http://x"}""") ==> true
    }
    test("observabilityLogs") {
      val config = UnitConfig(
        observability = Some(Observability("svc", logs = Some(Logs()))),
        applications = Map("extra" -> Application(executable = Some("/bin/extra")))
      )
      val json = config.toJson("/bin/app", "/logs")
      json.contains(""""access_log":{"path":"/logs/access.log"""") ==> true
      json.contains(""""stdout":"/logs/app.stdout.log","stderr":"/logs/app.stderr.log"""") ==> true
      json.contains(""""stdout":"/logs/extra.stdout.log"""") ==> true
      config.logFiles("/logs").map(_._1) ==> Seq(
        "access",
        "unit",
        "app.stdout",
        "app.stderr",
        "extra.stdout",
        "extra.stderr"
      )
      UnitConfig().logFiles("/logs") ==> Nil
      UnitConfig(observability = Some(Observability("svc"))).toJson("/bin/app").contains(""""stdout":""") ==> false
    }
    test("stack") {
      val config = UnitConfig(
        observability =
          Some(Observability("svc", stack = Some(Stack(directory = Some("/stack"), dashboardsPort = 3001))))
      )
      val json = config.toJson("/bin/app", "/work/logs", "/work")
      Seq("dashboards" -> 3001, "metrics" -> 9090, "traces" -> 16686, "logs" -> 9428, "traces" -> 4318).foreach {
        (name, port) =>
          json.contains(s""""127.0.0.1:$port":{"pass":"applications/$name"}""") ==> true
      }
      json.contains(""""executable":"/stack/dashboards/snunit-dashboards"""") ==> true
      json.contains(""""executable":"/stack/logs/snunit-logs"""") ==> true
      json.contains(""""arguments":["--config=/work/stack/traces.yaml"]""") ==> true
      // The metrics, the logs and the traces go to the stack.
      json.contains(""""telemetry":{"endpoint":"http://localhost:4318/v1/traces"""") ==> true
      config.logFiles("/work/logs", "/work").map(_._1).contains("logs.stdout") ==> true
      val files = config.stackFiles("/work").toMap
      files("/work/stack/metrics.yml").contains("127.0.0.1:9464") ==> true
      files("/work/stack/traces.yaml").contains("endpoint: 127.0.0.1:4318") ==> true
      files("/work/stack/traces.yaml").contains("keys: /work/data/traces/keys") ==> true
      files("/work/stack/dashboards.yaml").contains("folder: /work/data/dashboards/db") ==> true
      files("/work/stack/dashboards.yaml").contains("archive_path: /stack/dashboards/plugins-archive") ==> true
      files("/work/stack/dashboards/provisioning/datasource.json").contains("http://127.0.0.1:9090") ==> true
      files("/work/stack/dashboards/provisioning/dashboard.json").contains("rate(snunit_requests_total[30s])") ==> true
      files("/work/stack/dashboards/provisioning/dashboard.json").contains("http://127.0.0.1:16686") ==> true
      config.missingStackPrograms.sorted ==> Seq("dashboards", "logs", "metrics", "traces")
      // Only the fixed ports have to be free, the internal ones are any free port.
      config.observability.flatMap(_.stack).get.fixedPorts.map(_._3).sorted ==> Seq(3001, 4318, 9090, 9428, 16686)
      config.observability.flatMap(_.stack).get.urls.toMap.apply("dashboards") ==> "http://127.0.0.1:3001"
      // The metrics are scraped, not pushed to the traces program (it would answer 404).
      config.resolvedObservability.get.metricsInterval ==> None
      // Unless they go to another collector.
      UnitConfig(observability =
        Some(Observability("svc", otlpEndpoint = "http://collector:4319", stack = Some(Stack())))
      ).resolvedObservability.get.metricsInterval.isDefined ==> true
      // The logs go to VictoriaLogs.
      val logs = config.resolvedObservability.flatMap(_.logs).get
      logs.url ==> "http://127.0.0.1:9428"
      logs.pushPath ==> "/insert/loki/api/v1/push"
      // Own data directory, and no stack without Stack.
      UnitConfig(observability = Some(Observability("svc", stack = Some(Stack(dataDirectory = Some("/data"))))))
        .stackFiles("/work")
        .toMap
        .apply("/work/stack/traces.yaml")
        .contains("keys: /data/traces/keys") ==> true
      UnitConfig().stackFiles("/work") ==> Nil
      assertThrows[IllegalArgumentException] {
        UnitConfig(
          applications = Map("dashboards" -> Application(executable = Some("/x"))),
          observability = Some(Observability("svc", stack = Some(Stack())))
        ).toJson("/bin/app")
      }
    }
    test("defaultStack") {
      UnitConfig().withDefaultStack("svc").observability.flatMap(_.stack).nonEmpty ==> true
      UnitConfig().withDefaultStack("svc").observability.map(_.serviceName) ==> Some("svc")
      val own = UnitConfig(observability = Some(Observability("mine")))
      own.withDefaultStack("svc").observability.map(_.serviceName) ==> Some("mine")
      val configured = UnitConfig(observability = Some(Observability("a", stack = Some(Stack(dashboardsPort = 1)))))
      configured.withDefaultStack("svc") ==> configured
    }
    test("observabilityValidation") {
      def invalid(config: UnitConfig): Unit = assertThrows[IllegalArgumentException] { config.toJson("/bin/app") }
      invalid(UnitConfig(observability = Some(Observability(""))))
      invalid(UnitConfig(observability = Some(Observability("a", otlpEndpoint = "https://collector:4318"))))
      invalid(UnitConfig(observability = Some(Observability("a", traces = Some(Traces(samplingRatio = Some(2.0)))))))
      invalid(UnitConfig(sidecars = Seq(Sidecar("a", "x"), Sidecar("a", "y"))))
      UnitConfig(observability = Some(Observability("a", otlpEndpoint = "https://c", metricsInterval = None)))
        .toJson("/bin/app")
        .nonEmpty ==> true
    }
    test("validation") {
      def invalid(config: UnitConfig): Unit = assertThrows[IllegalArgumentException] { config.toJson("/bin/app") }
      invalid(UnitConfig(listeners = Nil))
      invalid(UnitConfig(listeners = Seq(Listener(pass = Pass.Route("missing")))))
      invalid(UnitConfig(listeners = Seq(Listener(pass = Pass.Upstream("missing")))))
      invalid(UnitConfig(listeners = Seq(Listener(tls = Some(Tls(Seq("missing")))))))
      invalid(UnitConfig(listeners = Seq(Listener(), Listener())))
      invalid(UnitConfig(certificates = Map("../escape" -> "PEM")))
      invalid(UnitConfig(routes = Map("r" -> Seq(RouteStep(Action.PassTo(Pass.Route("missing")))))))
    }
  }
}
