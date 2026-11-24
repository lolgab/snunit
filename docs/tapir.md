---
title: Tapir
---

SNUnit offers interpreters for [Tapir](https://tapir.softwaremill.com) server endpoints.
You can write all your application using Tapir and then convert your Tapir endpoints
with logic into a SNUnit `Handler`.

Currently two interpreters are available:

| Artifact | Interpreter | Example |
|---|---|---|
| `snunit-tapir` | `SNUnitIdServerInterpreter`, which works best with `SyncServerBuilder` for synchronous applications | [in tests](https://github.com/lolgab/snunit/blob/main/integration/tests/tapir-helloworld/src/Main.scala) |
| `snunit-tapir-cats-effect` | An interpreter for cats-effect, hidden behind `snunit.tapir.SNUnitServerBuilder` | [in tests](https://github.com/lolgab/snunit/blob/main/integration/tests/tapir-helloworld-cats-effect/src/Main.scala) |

## Automatic server creation

`snunit.TapirApp` extends `cats.effect.IOApp` building the SNUnit server.

It exposes a `def serverEndpoints: Resource[IO, List[ServerEndpoint[Any, IO]]]` that you need to
implement with your server logic.

Here is an example "Hello world" app:

```scala
//> using dep com.github.lolgab::snunit-tapir-cats-effect::{{ site.snunit_version }}
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

To [configure](configuration.html) the server, override `unitConfig`.

## WebSockets

Tapir `webSocketBody` endpoints are supported by both interpreters.

### With cats-effect

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

Example [in tests](https://github.com/lolgab/snunit/blob/main/integration/tests/tapir-websocket/src/Main.scala).

### Synchronous

The synchronous interpreter doesn't depend on fs2. It uses `snunit.tapir.SNUnitStreams`, where a pipe
is a function `A => Iterable[B]` called for each incoming message, returning the messages to send back.

This has two limits:

- Pipes run on the server event loop, so they must not block.
- The server can't send messages that aren't a reply to an incoming one.

Remember to also call `setWebsocketHandler(websocketHandler)` on the `SyncServerBuilder`:

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

Example [in tests](https://github.com/lolgab/snunit/blob/main/integration/tests/tapir-websocket-sync/src/Main.scala).

## Tracing

See [Tracing with otel4s](otel4s.html).
