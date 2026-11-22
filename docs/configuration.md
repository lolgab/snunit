---
title: Configuration
---

The executable is configured in code, with a `snunit.config.UnitConfig`. It is a Scala API for
the [FreeUnit configuration](https://freeunit.org/): listeners (with TLS), routes (static files,
proxying, redirects...), upstreams, HTTP settings (timeouts, `max_body_size`...), application processes
and limits, isolation, access log and telemetry. What you don't set keeps the FreeUnit default.

```scala
//> using dep com.github.lolgab::snunit::{{ site.snunit_version }}
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

The configuration is only used when the executable is started directly. It is ignored when the
executable runs under a FreeUnit you configured yourself.

## Where to pass it

| Server | How |
|---|---|
| `SyncServerBuilder` | `.setConfig(config)` |
| `SNUnitServerBuilder` (http4s, Tapir) | `.withConfig(config)` |
| `Http4sApp`, `TapirApp` | override `def unitConfig: ResourceIO[UnitConfig]` |

## Loading values

`UnitConfig` is a plain case class, so you can read the port from the environment or from a file the
way you prefer, for example `UnitConfig().withPort(sys.env("PORT").toInt)`.

In `Http4sApp` and `TapirApp`, `unitConfig` is a `ResourceIO`, so you can also load it with an effect,
for example with `cats.effect.std.Env`:

```scala
override def unitConfig = Resource.eval(
  Env[IO].get("PORT").map(port => UnitConfig().withPort(port.fold(8080)(_.toInt)))
)
```

## HTTPS

For HTTPS you need a certificate bundle (certificate chain and private key in PEM format).
Give it a name in `certificates` and use the name in `Listener.tls`:

```scala
UnitConfig(
  listeners = Seq(Listener(address = "*:8443", tls = Some(Tls(Seq("main"))))),
  certificates = Map("main" -> scala.io.Source.fromFile("bundle.pem").mkString)
)
```

## Other applications

You can also run other programs built with a FreeUnit language module next to the application with
`UnitConfig.applications` (`Application(executable = ...)`) and `Pass.Application(name)`.

Programs that don't speak the FreeUnit protocol run as [sidecars](observability.html#sidecars).
