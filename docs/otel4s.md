---
title: Tracing with otel4s
---

[otel4s](https://typelevel.org/otel4s) is configured by the `OTEL_*` variables SNUnit gives to the application, so
`Otel4sSdk.resource` works with `snunit run --stack` or with your own collector. `Otel4sHttp4s.middleware` and
`Otel4sTapir.interceptor` make every request a server span, a child of the span FreeUnit sends. You create your own
spans with the same tracer.

## http4s

```scala
//> using dep com.github.lolgab::snunit-http4s0.23::{{ site.snunit_version }}
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

## Tapir

```scala
//> using dep com.github.lolgab::snunit-tapir-cats-effect::{{ site.snunit_version }}
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

## Other frameworks

Use otel4s as usual and continue the trace with `tracer.joinOrRoot(headers)`, where `headers` are
the lowercased request headers.

## What you need to build

The modules depend on otel4s, but the Scala Native linker only keeps the code an application uses. Applications that
don't use it are the same size as before and don't need anything more.

The ones that do use it also link the TLS of fs2-io, so they need [s2n-tls](https://github.com/aws/s2n-tls) at build
time and at runtime.

| Platform | How to get s2n-tls |
|---|---|
| macOS | `brew install s2n` |
| Ubuntu | not packaged, build it with CMake as in [`build.yml`](https://github.com/lolgab/snunit/blob/main/.github/workflows/build.yml) |

## With the stack

With [the observability stack](stack.html), SNUnit sets `OTEL_LOGS_EXPORTER=none` and `OTEL_METRICS_EXPORTER=none`,
because its traces program only takes traces. The metrics of the launcher are scraped by Prometheus.
