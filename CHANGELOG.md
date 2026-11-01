# Changelog

## Unreleased

- Fix the Scala Native GC aborting an idle `SyncServer` after 60 seconds (`[ScalaNative GC|Error] FATAL: Timeout after 60.0s waiting for 1 thread(s)`) when other threads allocate: `listen()` waited in `nxt_unit_run`, an extern that is not `@blocking`, so the GC considered the thread running. Background threads (periodic workers, file watchers, long polling) are now safe. `listen()` waits for the ports of libunit with a `@blocking` `poll` and reads them with `nxt_unit_process_port_msg`, so the callbacks of libunit (the request handler...) run as managed code: Scala Native doesn't switch the thread back to `Managed` when foreign code calls a Scala callback, and there is no API for it. The externs of libunit that can wait for the router (`nxt_unit_response_send`, `nxt_unit_response_buf_alloc`, `nxt_unit_buf_send`...) are `@blocking`. `SyncServer.runOnce()` waits up to 20 milliseconds instead of until a message arrives. The public API is unchanged

## 0.13.0

- Publish the `sbt-snunit` plugin (sbt 1.x and 2.x, Scala Native 0.5) to Maven Central with every release: `addSbtPlugin("com.github.lolgab" % "sbt-snunit" % "<version>")`. It links `libunit`, embeds `unitd` and adds `snunitBundle`, `snunitBundleReleaseFast` and `snunitBundleReleaseFull`. It works with sbt 1.9+
- Update dependencies: Tapir 1.14.0, cask 0.11.3 (`cask.patch` rebased), scala-java-time 2.8.0, sttp shared 1.6.0, sbt 2.0.11. Scala stays 3.9.0 and Mill 1.1.10 (Mill 1.3.0 hangs in `prepareOffline`)
- cask: the virtual thread handler executor of cask 0.11 is disabled in `cask.patch` (`java.lang.invoke` doesn't exist on Scala Native), requests always use the blocking handler. Add `Undertow.stop()` to `snunit-undertow`, a no-op: the launcher shuts the server down on SIGTERM, SIGINT and SIGHUP
- Add otel4s tracing for http4s and Tapir: `Otel4sSdk.resource` (the SDK configured by the `OTEL_*` variables of `Observability`, `snunit run --stack` included), `Otel4sHttp4s.middleware` and `Otel4sTapir.interceptor` (a server span for every request, a child of the span FreeUnit sends: the `traceparent` header is continued). `snunit-async-cats-effect` depends on otel4s, but the code is only linked into the applications that use it. Also add `SNUnitServerBuilder.withInterceptors` (Tapir)
- With the stack, the application gets `OTEL_LOGS_EXPORTER=none` and `OTEL_METRICS_EXPORTER=none` (when `otlpEndpoint` is the receiver of the stack), so an OpenTelemetry SDK doesn't push logs and metrics to the traces program, which answers 404 to every push

## 0.12.2

- Fix `IllegalStateException` from `CharsetEncoder.flush` under concurrent requests (for example `snunit-tapir-cats-effect` under load): the string encoders, decoders and buffers were shared between threads
- Don't read and write the internals of `String` through raw pointers: Scala Native compact object headers can be enabled. The CLI doesn't pass `--native-compact-headers=false` anymore
- The launcher checks that the fixed ports of the stack are free and says which is not, prints where the programs are, and stops the programs of the stack that stay running when `unitd` exits
- The OTLP/HTTP receiver of the stack (port 4318) goes through FreeUnit, with the Jaeger UI (new stack release `stack-v0.0.2`). The internal ports of the programs are any free port
- With the stack, the metrics are scraped by Prometheus and not also pushed to the traces program (it answered 404 to every push)
- Document every port of the stack
- The `snunit` CLI doesn't add the SNUnit dependency anymore (a pinned version was not binary compatible with the other modules): add it with `//> using dep` as in the examples. Removes `SNUNIT_VERSION`

## 0.12.1

- The JSON access log has a `_msg` field (`GET /path 200`), so log servers that need a message (VictoriaLogs) show something readable
- The dashboard of the observability stack has stat panels, filled charts and clearer links

## 0.12.0

- Add the observability stack: `Observability.stack` (`snunit.config.Stack`) runs metrics, traces, logs and dashboards programs inside FreeUnit with the application, connected to it. They are modified builds of Prometheus, Jaeger, VictoriaLogs and Perses (all Apache-2.0). `snunit stack install` downloads them (built, with their licenses, by the `stack-*` release workflow) and `snunit run --stack` runs an application with them
- Add `UnitConfig.applications` and `Pass.Application` to run other programs (built with a FreeUnit language module) next to the SNUnit application, and `Observability.logs` to ship the logs of FreeUnit and of the applications to Loki or VictoriaLogs
- Add `UnitConfig.observability` and `UnitConfig.sidecars`: OpenTelemetry tracing and `OTEL_*` variables for the application, a JSON access log, a Prometheus endpoint and OTLP push of the FreeUnit status metrics, and supervised sidecar processes (OpenTelemetry collector...) run with the executable
- Add `snunit.config.UnitConfig`, a Scala API covering the FreeUnit configuration (listeners, TLS, routes, upstreams, HTTP settings, application processes, limits, isolation, access log, telemetry...). Pass it to `SyncServerBuilder.setConfig`, `SNUnitServerBuilder.withConfig` or override `unitConfig` (a `ResourceIO[UnitConfig]`) in `Http4sApp` and `TapirApp`. It replaces the `SNUNIT_PORT` and `SNUNIT_PROCESSES` environment variables
- Standalone executables shut down gracefully on SIGTERM, SIGINT and SIGHUP: the listeners are closed and the running requests finish (up to `UnitConfig.shutdownTimeout`). A second signal stops without waiting
- Standalone executables don't leave `unitd` running and the temporary directory behind when they are killed with SIGKILL
- Standalone executables exit with an error when FreeUnit rejects the configuration
- Add `Request.contentSize` and `Request.readContent` to read the request body in chunks
- Fix the bounds check of `Request.headerName` and `Request.headerValue`

- Support WebSockets in Tapir: `webSocketBody` endpoints with `fs2.Pipe`s in `snunit-tapir-cats-effect` and with the fs2-free `SNUnitStreams` in `snunit-tapir`
- Support WebSockets in http4s with `SNUnitServerBuilder.withHttpWebSocketApp`
- Support http4s 1.x again
- Add `WebsocketConnections`, `Request.id` and `Request.sendWebsocketFrame` to the core module

## 0.11.0

- Add the `snunit` command line tool (`snunit run`, `snunit package`) that builds a single executable embedding FreeUnit
- Applications start `unitd` themselves when run directly (configurable with the `SNUNIT_PORT` and `SNUNIT_PROCESSES` environment variables)
- Remove the Sbt and Mill plugins in favor of the CLI
- The CLI is released separately from the library (`cli-v*` tags), with binaries for Linux and macOS (x86_64 and aarch64), `snunit bundle` and `snunit link-flags` to use it without scalino

## 0.1.1

- Implement plugins for Sbt and Mill to run apps locally
- Bump various dependencies

## 0.1.0

- Delete the `snunit-routes` module based on trail
- Rename the Tapir integration files to match Tapir's conventions
