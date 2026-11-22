---
title: http4s
---

SNUnit offers a server implementation for [http4s](https://http4s.org).
It runs on the cats-effect runtime, which on Scala Native polls with epoll or kqueue.

| Artifact | http4s |
|---|---|
| `snunit-http4s0.23` | 0.23 |
| `snunit-http4s1` | the 1.0 milestones |

There are two ways you can build a http4s server.

## Automatic server creation

`snunit.Http4sApp` extends `cats.effect.IOApp` building the SNUnit server.

It exposes a `def routes: Resource[IO, HttpApp[IO]]` that you need to implement with your
server logic.

Here is an example "Hello world" app:

```scala
//> using dep com.github.lolgab::snunit-http4s0.23::{{ site.snunit_version }}
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

To [configure](configuration.html) the server, override `unitConfig`.

## Manual server creation

If you want to have more control over the server creation, you can use
`SNUnitServerBuilder` yourself.

For example, here you see it in combination with `cats.effect.IOApp`:

```scala
//> using dep com.github.lolgab::snunit-http4s0.23::{{ site.snunit_version }}
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

## WebSockets

Use `withHttpWebSocketApp` to get the websocket builder and create websocket routes as with any other
http4s server. The builder is `WebSocketBuilder2` in http4s 0.23 and `WebSocketBuilder` in 1.x.
`snunit.http4s.SNUnitWebSocketBuilder` is an alias for the right one.

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

You can find an example [in tests](https://github.com/lolgab/snunit/blob/main/integration/tests/http4s-websocket/src/Main.scala).

## Tracing

See [Tracing with otel4s](otel4s.html).
