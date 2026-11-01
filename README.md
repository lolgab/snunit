# SNUnit: Scala Native HTTP server based on FreeUnit

```scala
//> using dep com.github.lolgab::snunit::0.13.0
import snunit.*

@main
def run =
  SyncServerBuilder
    .setRequestHandler(req =>
      req.send(
        statusCode = StatusCode.OK,
        content = "Hello world!\n",
        headers = Headers("Content-Type" -> "text/plain")
      )
    )
    .build()
    .listen()
```

SNUnit is a Scala Native library to write HTTP server applications on top of
[FreeUnit](https://freeunit.org/) (community LTS fork of NGINX Unit). It allows you to write both synchronous
and asynchronous web servers with automatic restart on crashes, automatic
load balancing of multiple processes, great performance and all the nice
[FreeUnit features](https://freeunit.org/).

An SNUnit application is a **single executable**: it embeds `unitd`, starts it
with a generated configuration and runs itself as the application. There is
nothing to install besides the `snunit` command line tool.

## Getting started

Install [scalino](https://github.com/lolgab/scalino), then the `snunit` command line tool
(Linux and macOS, x86_64 and aarch64):

```bash
curl -fsSL https://raw.githubusercontent.com/lolgab/snunit/main/install.sh | bash
```

Or download the archive for your platform from the
[releases](https://github.com/lolgab/snunit/releases) tagged `cli-v*`. The CLI is released
independently of the library: it doesn't add any SNUnit dependency, so your code picks the library
version with a `//> using dep` directive, as in the examples.

Then write the
`Hello.scala` above (no build file needed, the `//> using dep` line is enough) and run:

```bash
snunit run Hello.scala
```

This builds the app and serves it on <http://localhost:8080>. Use `-w` to rebuild and
restart on every change:

```bash
snunit run -w Hello.scala
```

To build the single executable to deploy somewhere else:

```bash
snunit package Hello.scala -o hello
./hello
```

### Using sbt

The `sbt-snunit` plugin (sbt 1.x and 2.x, with `sbt-scala-native` 0.5) links `libunit` and embeds `unitd`
for you, with no CLI needed:

```scala
// project/plugins.sbt
addSbtPlugin("com.github.lolgab" % "sbt-snunit" % "<version>")

// build.sbt
lazy val app = project
  .enablePlugins(ScalaNativePlugin, SNUnitPlugin)
  .settings(libraryDependencies += "com.github.lolgab" %%% "snunit" % "<version>") // %% on sbt 2
```

`sbt app/snunitBundle` builds the single executable to deploy (`snunitBundleReleaseFast` and
`snunitBundleReleaseFull` link with `nativeLinkReleaseFast` and `nativeLinkReleaseFull` instead). On Linux it is a copy of the linked
binary with `unitd` appended, on macOS `unitd` is embedded while linking. `snunitPlatform` picks the
platform to bundle for (default: this machine). FreeUnit is downloaded the first time and cached in
`~/.cache/snunit`, like the CLI does.

### Using another build tool

Already building with sbt, Mill or scala-cli? `snunit` can add `unitd` to an executable you
built yourself, without scalino:

```bash
# Linux: appends unitd to the executable (add --platform linux-aarch64 etc. to bundle for another target)
snunit bundle target/scala-3.3.0/app -o app-bundled
```

The executable still needs `libunit.a` at link time.
`snunit link-flags` downloads FreeUnit and prints the linker options to use, one per line.
On macOS `unitd` can only be embedded while linking, so there you pass these options to your
build tool instead of running `snunit bundle`: they include
`-Wl,-sectcreate,__DATA,__unitd,<path to unitd>`.

`snunit` forwards every other argument to `scalino`, so `//> using` directives and
the usual flags work (for example `//> using dep` to add a library).
Program arguments go after `--`: `snunit run Hello.scala -- --my-flag`.

`snunit` downloads the prebuilt FreeUnit for your platform (Linux and macOS, x86_64 and
aarch64) the first time and caches it in `~/.cache/snunit`.

### Scala.js client

The server is a Scala Native program, so scalino's Scala.js support gives it a frontend without a JVM.
Declare the client (a Scala.js project) and the sources shared by both sides in the server:

```scala
//> using jsClient ../client
//> using shared ../shared
```

`snunit run` builds the client and embeds it in the executable as the resource `/client/main.js`
(`snunit package` links it in release mode), and `--watch` also rebuilds when the client or the
shared sources change. Serve it from a handler with `getClass.getResourceAsStream("/client/main.js")`.
To serve the client with FreeUnit's static files instead (a `share` route), add
`//> using jsClientOut ../client-dist` and nothing is embedded. See scalino's
[`examples/client-server`](https://github.com/lolgab/scalino/tree/main/examples/client-server).

### Configuration

The running executable is configured in code with a `snunit.config.UnitConfig`. It covers
the [FreeUnit configuration](https://freeunit.org/): listeners (with TLS), routes (static files,
proxying, redirects...), upstreams, HTTP settings (timeouts, `max_body_size`...), application processes
and limits, isolation, access log and telemetry. Whatever you don't set keeps the FreeUnit default.

```scala
//> using dep com.github.lolgab::snunit::0.13.0
import snunit.*
import snunit.config.*
import scala.concurrent.duration.*

@main
def run =
  SyncServerBuilder
    .setConfig(
      UnitConfig(
        listeners = Seq(Listener.port(9000)),
        application = Application(
          processes = Some(Processes.Dynamic(max = 8, spare = 2)),
          limits = Some(Limits(timeout = Some(30.seconds)))
        ),
        http = HttpSettings(maxBodySize = Some(1024 * 1024)),
        shutdownTimeout = 20.seconds
      )
    )
    .setRequestHandler(req => req.send(StatusCode.OK, "Hello world!\n", Headers.empty))
    .build()
    .listen()
```

`UnitConfig` is a plain case class: read the port from the environment or from a file the way you prefer,
for example `UnitConfig().withPort(sys.env("PORT").toInt)`.

With the other servers pass it to `SNUnitServerBuilder.withConfig(config)`, or override
`def unitConfig: ResourceIO[UnitConfig]` in `Http4sApp` and `TapirApp`, which lets you load it
effectfully, for example with `cats.effect.std.Env`:

```scala
override def unitConfig = Resource.eval(
  Env[IO].get("PORT").map(port => UnitConfig().withPort(port.fold(8080)(_.toInt)))
)
```

HTTPS needs certificate bundles (certificate chain and private key in PEM format),
which you give by name in `certificates` and reference from `Listener.tls`:

```scala
UnitConfig(
  listeners = Seq(Listener(address = "*:8443", tls = Some(Tls(Seq("main"))))),
  certificates = Map("main" -> scala.io.Source.fromFile("bundle.pem").mkString)
)
```

The configuration only applies when the executable is started directly. It is ignored when the
executable runs under a FreeUnit you configured yourself.

### Observability

`UnitConfig.observability` wires the application to the usual metrics, tracing and logging tools
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

- **Traces**: FreeUnit sends a span for every request to `otlpEndpoint` and passes the `traceparent`
  header to the application. The application processes get the standard `OTEL_SERVICE_NAME`,
  `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_RESOURCE_ATTRIBUTES`... variables, so any OpenTelemetry SDK or
  agent in the application is configured and continues the same trace. FreeUnit's own spans are
  reported with the service name `NGINX Unit`.
- **Metrics**: the launcher reads the FreeUnit status and exposes connections, requests, application
  processes and sidecar state (`snunit_*`) at `http://127.0.0.1:9464/metrics` for Prometheus, and
  pushes them as OTLP every `metricsInterval` (15 seconds) to `otlpEndpoint`. Pushing needs a plain
  `http://` endpoint: use a local collector to forward to a remote one.
- **Logs**: one JSON object per request (OpenTelemetry HTTP attribute names, with the `traceparent`
  header to correlate with traces) is written to the standard output next to the FreeUnit log, where
  a collector or the container runtime collects it. Set `accessLog = false` to disable it, or give
  your own `UnitConfig.accessLog`.

- **Logs shipping**: FreeUnit only writes logs to files and has no way to send them. With
  `logs = Some(Logs(url = "http://localhost:3100"))` the launcher has FreeUnit write the access log,
  its own log and the output of the applications to files, follows them and pushes the new lines to
  a server with the Loki push protocol (Loki, VictoriaLogs...) every second (still copying them to
  the standard output). Streams are labelled `service_name`, `job="snunit"` and `source`
  (`access`, `unit`...). Lines the server can't take yet are kept in memory and sent later, so the
  server can be one of the FreeUnit applications starting at the same time.

What you set explicitly in `telemetry`, `accessLog` or `application.environment` wins over what
`observability` generates.

#### Sidecars

FreeUnit only runs applications speaking its protocol, so tools like an OpenTelemetry collector
run as `Sidecar`s: processes the launcher starts with FreeUnit, restarts when they exit (with a
growing delay), and stops after the running requests have finished, so that they receive the last
telemetry. They are stopped even if the launcher is killed. `Sidecar.otelCollector`
covers the common one, any other process is a `Sidecar(name, command, arguments)`. Expose one next to
the application with a route:

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

#### The observability stack, inside FreeUnit

Metrics, traces, logs and dashboards programs can run inside FreeUnit together with your application, already connected
to it. They are modified builds of [Prometheus](https://prometheus.io) (metrics), [Jaeger](https://www.jaegertracing.io)
(traces), [VictoriaLogs](https://docs.victoriametrics.com/victorialogs/) (logs) and [Perses](https://perses.dev)
(dashboards): all Apache-2.0, Prometheus and Jaeger are CNCF graduated projects and Perses is a CNCF project (the CNCF
has no project that stores logs). The CLI installs them (they are built by the CI of this repository and downloaded with
their checksums):

```bash
snunit stack install              # once: ~/.cache/snunit/stack
snunit run --stack Hello.scala    # run with the stack, no change in the code
```

| | | |
|---|---|---|
| dashboards | request rate, connections, processes | <http://localhost:3000> |
| traces | one span per request | <http://localhost:16686> |
| logs | access log, FreeUnit log, output of the programs | <http://localhost:9428/select/vmui> |
| metrics | the metrics endpoint of the launcher, scraped | <http://localhost:9090> |

#### Tracing the application with otel4s

[otel4s](https://typelevel.org/otel4s) is configured by the `OTEL_*` variables SNUnit gives to the application, so
`Otel4sSdk.resource` works with `snunit run --stack` or your own collector. `Otel4sHttp4s.middleware` and
`Otel4sTapir.interceptor` make every request a server span, a child of the span FreeUnit sends, and you create your own
spans with the same tracer.

With http4s:

```scala
//> using dep com.github.lolgab::snunit-http4s0.23::0.13.0
import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import snunit.otel4s.{Otel4sHttp4s, Otel4sSdk}

object Main extends snunit.Http4sApp {
  def routes = for {
    otel4s <- Otel4sSdk.resource
    tracer <- Resource.eval(otel4s.tracerProvider.get("my-service"))
  } yield Otel4sHttp4s.middleware(tracer)(
    HttpRoutes.of[IO] { case GET -> Root / "hello" => tracer.span("work").surround(Ok("hello")) }.orNotFound
  )
}
```

With Tapir:

```scala
//> using dep com.github.lolgab::snunit-tapir-cats-effect::0.13.0
import cats.effect.*
import snunit.otel4s.{Otel4sSdk, Otel4sTapir}
import snunit.tapir.SNUnitServerBuilder
import sttp.tapir.*

object Main extends IOApp.Simple {
  def run = Otel4sSdk.resource.use { otel4s =>
    otel4s.tracerProvider.get("my-service").flatMap { tracer =>
      val hello = endpoint.get
        .in("hello")
        .out(stringBody)
        .serverLogicSuccess[IO](_ => tracer.span("work").surround(IO.pure("hello")))
      SNUnitServerBuilder
        .default[IO]
        .withInterceptors(Otel4sTapir.interceptor(tracer) :: Nil)
        .withServerEndpoints(hello :: Nil)
        .run
    }
  }
}
```

The modules depend on otel4s, but the Scala Native linker only keeps the code an application uses: the applications that
don't use it are the same size as before and don't need anything more. The ones that do also link the TLS of fs2-io, so
they need [s2n-tls](https://github.com/aws/s2n-tls) at build time and at runtime (`brew install s2n` on macOS; on Linux it
isn't packaged by Ubuntu, build it with CMake as in [`build.yml`](.github/workflows/build.yml)).

With another framework, use otel4s as usual and continue the trace with `tracer.joinOrRoot(headers)`, where `headers` are
the lowercased request headers. With the stack, SNUnit sets `OTEL_LOGS_EXPORTER=none` and `OTEL_METRICS_EXPORTER=none`
because its traces program only takes traces: the metrics of the launcher are scraped by Prometheus.

### Ports

The first five ports listen on `bindAddress` (`127.0.0.1` by default), the internal ones only on `127.0.0.1`. Your application keeps its own port (8080 by default).
The ports are fields of `Stack`, so they can be changed. On start the application checks that the fixed ones are free (and says which is not), and prints where the programs are.

| Port | Used by | Reachable through FreeUnit | `Stack` field |
|---|---|---|---|
| 3000 | dashboards, web UI | yes | `dashboardsPort` |
| 9090 | metrics, web UI and API | yes | `metricsPort` |
| 16686 | traces, web UI and API | yes | `tracesPort` |
| 9428 | logs, web UI (`/select/vmui`) and API | yes | `logsPort` |
| 4318 | traces, OTLP/HTTP receiver: FreeUnit sends the spans here (`/v1/traces`) | yes, to the same program as the UI | `tracesOtlpPort` |
| any free port | internal ports of dashboards, traces (HTTP, gRPC) and logs behind FreeUnit, on `127.0.0.1` only | no, not meant to be used | `dashboardsInternalPort`, `tracesInternalPort`, `tracesGrpcPort`, `logsInternalPort` (0 = any free port) |

To have it in the code, and to configure it:

```scala
UnitConfig(observability = Some(Observability("my-service", stack = Some(Stack(dataDirectory = Some("/var/lib/my-service"))))))
```

By default the data is temporary. To deploy, install the stack where the executable runs
(`snunit stack install --dir /opt/snunit-stack`, then `SNUNIT_STACK_DIR=/opt/snunit-stack`), for example in your
Dockerfile. The programs only listen on the loopback interface and have no login: put your own authentication in front
before exposing them.

**These are not the official releases** of those projects: they are built from their sources with a small change (when
started by FreeUnit they serve HTTP through it), and they are not endorsed or supported by their authors. The names
belong to their owners. See [`stack/`](./stack) and its [licenses](./stack/LICENSES.md) (every archive has the licenses
of the program and of its dependencies, and the change we made). FreeUnit speaks HTTP/1.1 only, so gRPC doesn't go
through it, and every program runs with one process.

You can also run other programs built with a FreeUnit language module next to the application with
`UnitConfig.applications` (`Application(executable = ...)`) and `Pass.Application(name)`.

If you already run a stack, point `Observability.otlpEndpoint` to its OTLP/HTTP receiver, `Observability.logs` to a
Loki-compatible server (`Logs(url = ...)`), and let your Prometheus scrape `Observability.prometheus`.

### Background threads

The server can run other threads (periodic workers, file watchers, long polling) next to `listen()`, they can allocate
and run the garbage collector while the server waits for requests. `SyncServer` waits for FreeUnit in a way the
Scala Native GC knows about.

### Shutdown

On `SIGTERM`, `SIGINT` or `SIGHUP` the executable stops accepting connections and waits for the running requests
to finish, then exits. After `shutdownTimeout` (30 seconds by default) the remaining requests are interrupted. A second signal
stops without waiting, a third one kills the server. Open websocket connections count as running requests.

If the executable is killed with `SIGKILL`, or in any other way it can't handle, a small watchdog process
stops FreeUnit and removes the temporary files.

### How it works

When the executable is started directly it extracts the embedded `unitd` (from memory
with `memfd_create` on Linux, from a cache directory on macOS), writes a `conf.json` in a temporary
state directory, starts `unitd` and forwards signals to it. FreeUnit then starts
the same executable as an `external` application. When started by FreeUnit,
the executable just serves requests.

## Sync and async support

SNUnit has two different server implementations.

With `SyncServerBuilder` you need to call `.listen()` to start listening.
It is a blocking operation so your process is stuck on listening and can't do
anything else while listening.
Moreover, all the request handlers need to respond directly and can't be implemented
using `Future`s or any other asyncronous mechanism since no `Future` will run, being
the process stuck on the `listen()` Unit event loop.
With http4s or tapir-cats-effect the server is automatically scheduled to run either on the
cats effect event loop, based on epoll/kqueue.
This allows you to complete requests asyncronously using whatever mechanism you prefer.
A process can accept multiple requests concurrently, allowing great parallelism.

## endpoints4s support

The `snunit-endpoints4s` artifact offers a synchronous [endpoints4s](https://github.com/endpoints4s/endpoints4s)
server interpreter: mix `snunit.endpoints4s.Endpoints` with your endpoint definitions and
pass `toHandler(endpoint.implementedBy(...))` to `SyncServerBuilder.setRequestHandler`.
You can find an example [in tests](./integration/tests/endpoints4s-helloworld/src/Main.scala).

## Tapir support

SNUnit offers interpreters for [Tapir](https://tapir.softwaremill.com) server endpoints.
You can write all your application using Tapir and the convert your Tapir endpoints
with logic into a SNUnit `Handler`.

Currently two interpreters are available:
- `SNUnitIdServerInterpreter` which works best with `SyncServerHandler` for synchronous applications
  - You can find an example [in tests](./integration/tests/tapir-helloworld/src/Main.scala)
- An interpreter for cats hidden behind `snunit.tapir.SNUnitServerBuilder` in the `snunit-tapir-cats-effect` artifact.
  - You can find an example [in tests](./integration/tests/tapir-helloworld-cats-effect/src/Main.scala)

### Automatic server creation

`snunit.TapirApp` extends `cats.effect.IOApp` building the SNUnit server.

It exposes a `def serverEndpoints: Resource[IO, List[ServerEndpoint[Any, IO]]]` that you need to
implement with your server logic.

Here an example "Hello world" app:

```scala
//> using dep com.github.lolgab::snunit-tapir-cats-effect::0.13.0
import cats.effect.*
import sttp.tapir.*

object Main extends snunit.TapirApp {
  def serverEndpoints = Resource.pure(
    endpoint.get
      .in("hello")
      .in(query[String]("name"))
      .out(stringBody)
      .serverLogic[IO](name => IO(Right(s"Hello $name!"))) :: Nil
  )
}
```

### WebSockets

Tapir `webSocketBody` endpoints are supported by both interpreters.

With `snunit-tapir-cats-effect` the pipe is an `fs2.Pipe`:

```scala
import cats.effect.*
import snunit.tapir.*
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.*

val echo = endpoint.get
  .in("echo")
  .out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](Fs2Streams[IO]))
  .serverLogicSuccess[IO](_ => IO.pure(identity))

SNUnitServerBuilder.default[IO].withServerEndpoints(echo :: Nil).run
```

The synchronous interpreter doesn't depend on fs2: it uses `snunit.tapir.SNUnitStreams`, where a pipe
is a function `A => Iterable[B]` called for each incoming message, returning the messages to send back.
Pipes run on the server event loop so they must not block, and the server can't send messages that aren't
a reply to an incoming one. Remember to also call `setWebsocketHandler(websocketHandler)` on the `SyncServerBuilder`:

```scala
import snunit.tapir.SNUnitIdServerInterpreter.*
import snunit.tapir.SNUnitStreams
import sttp.tapir.*

val echo = endpoint.get
  .in("echo")
  .out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](SNUnitStreams))
  .serverLogicSuccess[Id](_ => (message: String) => List(message))

snunit.SyncServerBuilder
  .setRequestHandler(toHandler(echo :: Nil))
  .setWebsocketHandler(websocketHandler)
  .build()
  .listen()
```

Examples [in tests](./integration/tests/tapir-websocket/src/Main.scala) and
[in tests (sync)](./integration/tests/tapir-websocket-sync/src/Main.scala).

## Http4s support

SNUnit offers a server implementation for [http4s](https://http4s.org).
It is based on the [epollcat](https://github.com/armanbilge/epollcat) asynchronous event loop.

There are two ways you can build a http4s server.

### Automatic server creation

`snunit.Http4sApp` extends `cats.effect.IOApp` building the SNUnit server.

It exposes a `def routes: Resource[IO, HttpApp[IO]]` that you need to implement with your
server logic.

Here an example "Hello world" app:

```scala
//> using dep com.github.lolgab::snunit-http4s0.23::0.13.0
//> using dep org.http4s::http4s-dsl::0.23.38
import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*

object app extends snunit.Http4sApp {
  def routes = Resource.pure(
    HttpRoutes
      .of[IO] { case GET -> Root =>
        Ok("Hello from SNUnit Http4s!")
      }
      .orNotFound
  )
}
```

### Manual server creation

If you want to have more control over the server creation, you can use the
`SNUnitServerBuilder` and manually use it.

For example, here you see it in combination with `cats.effect.IOApp`

```scala
//> using dep com.github.lolgab::snunit-http4s0.23::0.13.0
//> using dep org.http4s::http4s-dsl::0.23.38
import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import snunit.http4s.*

object Http4sHelloWorld extends IOApp.Simple {
  def helloWorldRoutes: HttpRoutes[IO] =
    HttpRoutes.of[IO] { case GET -> Root =>
      Ok("Hello Http4s!")
    }

  def run: IO[Unit] =
    SNUnitServerBuilder
      .default[IO]
      .withHttpApp(helloWorldRoutes.orNotFound)
      .run
}
```

### WebSockets

Use `withHttpWebSocketApp` to get the websocket builder (`WebSocketBuilder2` in http4s 0.23, `WebSocketBuilder` in 1.x,
available as `snunit.http4s.SNUnitWebSocketBuilder`) and create websocket routes as with any other http4s server:

```scala
SNUnitServerBuilder
  .default[IO]
  .withHttpWebSocketApp { webSocketBuilder =>
    HttpRoutes
      .of[IO] { case GET -> Root / "echo" =>
        webSocketBuilder.build(identity)
      }
      .orNotFound
  }
  .run
```

You can find an example [in tests](./integration/tests/http4s-websocket/src/Main.scala).
