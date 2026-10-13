package snunit.config

import scala.concurrent.duration.*

/** Metrics, traces and logs of an SNUnit application, wired to the usual tooling (OpenTelemetry collector, Prometheus,
  * Perses...).
  *
  *   - Traces: FreeUnit sends a span per request to `otlpEndpoint` and forwards the `traceparent` header to the
  *     application, so the spans of an OpenTelemetry SDK in the application join the same trace. The application
  *     processes get the standard `OTEL_*` environment variables, which any OpenTelemetry SDK or agent reads.
  *   - Metrics: the launcher reads the FreeUnit status (connections, requests, application processes) and the state of
  *     the [[Sidecar]]s, and exposes it as a Prometheus endpoint (`prometheus`) and/or pushes it as OTLP to
  *     `otlpEndpoint` (`metricsInterval`).
  *   - Logs: a JSON access log, with the trace id of each request, is written to the standard output next to the
  *     FreeUnit log, where a collector or the container runtime picks it up.
  *
  * {{{
  * UnitConfig(
  *   observability = Some(Observability("my-service", prometheus = Some(Prometheus()))),
  *   sidecars = Seq(Sidecar.otelCollector("collector.yaml"))
  * )
  * }}}
  *
  * Explicit `telemetry`, `accessLog` and `application.environment` in the [[UnitConfig]] take precedence over what this
  * generates.
  *
  * Spans created by FreeUnit itself are reported by FreeUnit with the service name `NGINX Unit`, `serviceName` applies
  * to the application and to the metrics.
  *
  * @param serviceName
  *   `service.name` of the application
  * @param otlpEndpoint
  *   base URL of an OTLP/HTTP receiver, for example a local OpenTelemetry collector (the default)
  * @param traces
  *   FreeUnit tracing to `otlpEndpoint`, `None` to disable it
  * @param metricsInterval
  *   how often the metrics are pushed to `otlpEndpoint`, `None` to not push them. Pushing supports plain `http://`
  *   endpoints only: use a local collector to forward to a remote one
  * @param prometheus
  *   serve the metrics for Prometheus to scrape
  * @param accessLog
  *   JSON access log on the standard output
  * @param logs
  *   ship the logs to Loki or VictoriaLogs, see [[Logs]]
  * @param stack
  *   run metrics, traces, logs and dashboards programs inside FreeUnit, connected to the application, see [[Stack]]
  */
final case class Observability(
    serviceName: String,
    serviceVersion: Option[String] = None,
    /** Extra OpenTelemetry resource attributes, for example `deployment.environment.name`. */
    resourceAttributes: Map[String, String] = Map.empty,
    otlpEndpoint: String = "http://localhost:4318",
    traces: Option[Traces] = Some(Traces()),
    metricsInterval: Option[FiniteDuration] = Some(15.seconds),
    prometheus: Option[Prometheus] = None,
    accessLog: Boolean = true,
    logs: Option[Logs] = None,
    stack: Option[Stack] = None
) {

  /** All the resource attributes, `service.name` first. */
  def resource: Seq[(String, String)] =
    Seq("service.name" -> serviceName) ++ serviceVersion.map("service.version" -> _) ++
      resourceAttributes.toSeq.sortBy(_._1)

  /** Standard OpenTelemetry SDK variables for the application processes. */
  private[config] def environment: Map[String, String] = {
    val base = Seq(
      "OTEL_SERVICE_NAME" -> serviceName,
      "OTEL_EXPORTER_OTLP_ENDPOINT" -> otlpEndpoint,
      "OTEL_EXPORTER_OTLP_PROTOCOL" -> "http/protobuf",
      "OTEL_RESOURCE_ATTRIBUTES" -> resource
        .drop(1)
        .map((k, v) => s"${Observability.percentEncode(k)}=${Observability.percentEncode(v)}")
        .mkString(",")
    ).filter(_._2.nonEmpty)
    val sampling = traces.flatMap(_.samplingRatio) match {
      case Some(ratio) =>
        Seq("OTEL_TRACES_SAMPLER" -> "parentbased_traceidratio", "OTEL_TRACES_SAMPLER_ARG" -> ratio.toString)
      case None => Nil
    }
    (base ++ sampling).toMap
  }

  private[config] def telemetry: Option[Telemetry] =
    traces.map(t =>
      Telemetry(
        endpoint = otlpEndpoint.stripSuffix("/") + "/v1/traces",
        batchSize = t.batchSize,
        protocol = Some("http"),
        samplingRatio = t.samplingRatio
      )
    )

  /** With a [[Stack]] the metrics, the logs and the traces go to it unless set otherwise. */
  private[config] def withStackDefaults: Observability = stack.fold(this) { s =>
    copy(
      prometheus = prometheus.orElse(Some(Prometheus())),
      logs = logs.orElse(
        // VictoriaLogs takes the Loki push protocol on its own path.
        Some(Logs(url = s"http://${s.localHost}:${s.logsPort}", pushPath = "/insert/loki/api/v1/push"))
      )
    )
  }

  private[config] def validate(): Unit = {
    require(serviceName.nonEmpty, "the service name can't be empty")
    require(otlpEndpoint.matches("https?://.+"), s"invalid OTLP endpoint '$otlpEndpoint'")
    require(
      metricsInterval.isEmpty || otlpEndpoint.startsWith("http://"),
      "the metrics can only be pushed to a plain http:// endpoint: point otlpEndpoint to a local collector or set " +
        "metricsInterval = None and let Prometheus scrape them"
    )
    require(metricsInterval.forall(_ >= 1.second), "metricsInterval must be at least 1 second")
    traces.flatMap(_.samplingRatio).foreach(r => require(r >= 0 && r <= 1, "samplingRatio must be between 0 and 1"))
    logs.foreach { l =>
      require(l.url.matches("http://.+"), s"the logs URL must be a plain http:// URL, not '${l.url}'")
    }
    prometheus.foreach(p => require(p.port > 0 && p.port < 65536, s"invalid Prometheus port ${p.port}"))
  }
}

object Observability {

  /** One JSON object per request, with the fields of the OpenTelemetry HTTP semantic conventions. */
  val jsonAccessLog: AccessLog = AccessLog(
    path = "/dev/stdout",
    format = Some(
      AccessLog.Format.Fields(
        Map(
          "time" -> "$time_local",
          "client.address" -> "$remote_addr",
          "http.request.method" -> "$method",
          "url.path" -> "$uri",
          "http.response.status_code" -> "$status",
          "http.response.body.size" -> "$body_bytes_sent",
          "user_agent.original" -> "$header_user_agent",
          "traceparent" -> "$header_traceparent"
        )
      )
    )
  )

  /** Percent-encoding of the OTEL_RESOURCE_ATTRIBUTES keys and values. */
  private def percentEncode(value: String): String = {
    val sb = new java.lang.StringBuilder
    value.getBytes("UTF-8").foreach { b =>
      val c = (b & 0xff).toChar
      if (c.isLetterOrDigit && c < 128 || "-._~".indexOf(c) >= 0) sb.append(c)
      else sb.append('%').append(String.format("%02X", Integer.valueOf(b & 0xff)))
    }
    sb.toString
  }
}

/** Tracing of the requests by FreeUnit. */
final case class Traces(
    /** Fraction of the requests traced, between 0 and 1. */
    samplingRatio: Option[Double] = None,
    batchSize: Option[Int] = None
)

/** The Prometheus scrape endpoint (`/metrics`) served by the launcher. */
final case class Prometheus(
    /** Loopback only by default: the endpoint is not authenticated. */
    host: String = "127.0.0.1",
    port: Int = 9464
)

/** Ships the logs to a server that takes the Loki push protocol (a simple HTTP and JSON API), like
  * [[https://docs.victoriametrics.com/victorialogs/ VictoriaLogs]] (the [[Stack]]).
  *
  * FreeUnit only writes logs to files: with this the launcher has FreeUnit write the access log, its own log and the
  * standard output and error of every application to files in its temporary directory, follows them and pushes the new
  * lines every second (`POST <url><pushPath>`, JSON). The lines are also copied to the standard output of the launcher
  * unless `echo` is false. Lines that can't be delivered are kept in memory, up to `maxBuffered`, and sent again.
  *
  * Streams get the labels `service_name`, `job="snunit"` and `source`: `access`, `unit`, or `<application>.stdout` /
  * `<application>.stderr`.
  *
  * @param url
  *   base URL of the server, plain `http://` only. It can be an application of the same FreeUnit
  * @param pushPath
  *   path of the push endpoint, `/loki/api/v1/push` for Loki
  * @param labels
  *   extra labels of every stream
  */
final case class Logs(
    url: String = "http://localhost:3100",
    pushPath: String = "/loki/api/v1/push",
    labels: Map[String, String] = Map.empty,
    echo: Boolean = true,
    maxBuffered: Int = 10000
)
