# Changelog

## Unreleased

- Add `snunit.config.UnitConfig`, a Scala API covering the FreeUnit configuration (listeners, TLS, routes, upstreams, HTTP settings, application processes, limits, isolation, access log, telemetry...). Pass it to `SyncServerBuilder.setConfig`, `SNUnitServerBuilder.withConfig` or override `unitConfig` in `Http4sApp` and `TapirApp`. It replaces the `SNUNIT_PORT` and `SNUNIT_PROCESSES` environment variables
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
