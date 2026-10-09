# SNUnit: Scala Native HTTP server based on FreeUnit

```scala
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
independently of the library: `snunit` depends on a pinned library version, which you can
change with `SNUNIT_VERSION`.

Then write the
`Hello.scala` above (no build file needed, `snunit` adds the SNUnit dependency) and run:

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

### Using another build tool

Already building with sbt, Mill or scala-cli? `snunit` can add `unitd` to an executable you
built yourself, without scalino:

```bash
# Linux: appends unitd to the executable (add --platform linux-aarch64 etc. to bundle for another target)
snunit bundle target/scala-3.3.0/app -o app-bundled
```

The executable still needs `libunit.a` at link time, and compact object headers disabled.
`snunit link-flags` downloads FreeUnit and prints the linker options to use, one per line.
On macOS `unitd` can only be embedded while linking, so there you pass these options to your
build tool instead of running `snunit bundle`: they include
`-Wl,-sectcreate,__DATA,__unitd,<path to unitd>`.

`snunit` forwards every other argument to `scalino`, so `//> using` directives and
the usual flags work (for example `//> using dep` to add a library).
Program arguments go after `--`: `snunit run Hello.scala -- --my-flag`.

`snunit` downloads the prebuilt FreeUnit for your platform (Linux x86_64/aarch64,
macOS arm64) the first time and caches it in `~/.cache/snunit`.

### Configuration

The running executable reads its configuration from environment variables:

| Variable | Default | |
|---|---|---|
| `SNUNIT_PORT` | `8080` | Port to listen on |
| `SNUNIT_PROCESSES` | FreeUnit default (1) | Number of application processes |

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
package snunit.tests

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
