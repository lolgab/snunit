---
title: Traces, metrics and logs
---

`UnitConfig.observability` connects the application to the usual tools for metrics, traces and logs
(OpenTelemetry, Prometheus, Jaeger, Perses):

```scala
UnitConfig(
  observability = Some(
    Observability(
      serviceName = "my-service",
      serviceVersion = Some("1.2.3"),
      otlpEndpoint = "http://localhost:4318", // an OTLP/HTTP receiver: a collector...
      prometheus = Some(Prometheus(port = 9464))
    )
  ),
  sidecars = Seq(Sidecar.otelCollector("collector.yaml"))
)
```

What you set explicitly in `telemetry`, `accessLog` or `application.environment` wins over what
`observability` generates.

If you only want to see it working, go to [the observability stack](stack.html):
it needs no configuration.

## Traces

FreeUnit sends a span for every request to `otlpEndpoint` and passes the `traceparent`
header to the application. The application processes get the standard `OTEL_SERVICE_NAME`,
`OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_RESOURCE_ATTRIBUTES`... variables, so an OpenTelemetry SDK or
agent in the application is already configured and continues the same trace.

FreeUnit's own spans are reported with the service name `NGINX Unit`.

To create spans in your code, see [Tracing with otel4s](otel4s.html).

## Metrics

The launcher reads the FreeUnit status and exposes connections, requests, application
processes and sidecar state as `snunit_*` metrics. Prometheus can scrape them at
`http://127.0.0.1:9464/metrics`, and they are also pushed as OTLP to `otlpEndpoint` every
`metricsInterval` (15 seconds).

Pushing needs a plain `http://` endpoint. To reach a remote one, forward through a local collector.

## Logs

One JSON object per request is written to the standard output, next to the FreeUnit log, where
a collector or the container runtime collects it. It uses the OpenTelemetry HTTP attribute names and has
the `traceparent` header, to correlate with traces. Set `accessLog = false` to disable it, or give
your own `UnitConfig.accessLog`.

### Shipping the logs

FreeUnit only writes logs to files and has no way to send them. The launcher can do it, with
`logs = Some(Logs(url = "http://localhost:3100"))`. FreeUnit then writes the access log,
its own log and the output of the applications to files. The launcher follows them and every second
pushes the new lines to a server with the Loki push protocol (Loki, VictoriaLogs...), still copying
them to the standard output.

Streams are labelled `service_name`, `job="snunit"` and `source` (`access`, `unit`...).
Lines the server can't take yet are kept in memory and sent later, so the
server can be one of the FreeUnit applications starting at the same time.

## Sidecars

FreeUnit only runs applications speaking its protocol. Tools like an OpenTelemetry collector don't,
so they run as `Sidecar`s: processes the launcher starts with FreeUnit and restarts when they exit, with a
growing delay. On shutdown they are stopped after the running requests have finished, so that they receive
the last telemetry. They are stopped even if the launcher is killed.

`Sidecar.otelCollector` covers the common one, any other process is a `Sidecar(name, command, arguments)`.
Expose one next to the application with a route:

```scala
UnitConfig(
  listeners = Seq(Listener(pass = Pass.Route("main"))),
  routes = Map(
    "main" -> Seq(
      RouteStep(Action.Proxy("127.0.0.1:3000"), Match(uri = Seq("/collector/*"))),
      RouteStep(Action.PassTo())
    )
  ),
  sidecars = Seq(Sidecar("my-tool", "/opt/my-tool/bin/my-tool", Seq("--port=3000")))
)
```
