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
[FreeUnit](https://freeunit.org/), the community LTS fork of NGINX Unit. It allows you to write both synchronous
and asynchronous web servers with automatic restart on crashes, automatic
load balancing of multiple processes, great performance and all the nice
[FreeUnit features](https://freeunit.org/).

An SNUnit application is a single executable. It embeds `unitd`, starts it
with a generated configuration and then runs itself as the application. There is
nothing to install besides the `snunit` command line tool.

You can use SNUnit directly, as in the example above, or through http4s, Tapir, Cask, Undertow
and endpoints4s.

## Documentation

The documentation is at **<https://lolgab.github.io/snunit/>**.

- [Getting started](https://lolgab.github.io/snunit/getting-started.html)
- [sbt and other build tools](https://lolgab.github.io/snunit/build-tools.html)
- [How it works](https://lolgab.github.io/snunit/how-it-works.html)
- [Configuration](https://lolgab.github.io/snunit/configuration.html)
- Libraries: [http4s](https://lolgab.github.io/snunit/http4s.html),
  [Tapir](https://lolgab.github.io/snunit/tapir.html),
  [Cask](https://lolgab.github.io/snunit/cask.html),
  [Undertow](https://lolgab.github.io/snunit/undertow.html),
  [endpoints4s](https://lolgab.github.io/snunit/endpoints4s.html)
- Observability: [traces, metrics and logs](https://lolgab.github.io/snunit/observability.html),
  [the observability stack](https://lolgab.github.io/snunit/stack.html),
  [tracing with otel4s](https://lolgab.github.io/snunit/otel4s.html)

Its sources are the Markdown files in [`docs/`](./docs). GitHub Pages builds them with Jekyll on every push to `main`.
To preview them locally: `jekyll serve --source docs`.

## Quick start

Install [scalino](https://github.com/lolgab/scalino), then the `snunit` command line tool
(Linux and macOS, x86_64 and aarch64):

```bash
curl -fsSL https://raw.githubusercontent.com/lolgab/snunit/main/install.sh | bash
```

Save the example above as `Hello.scala` and run it:

```bash
snunit run Hello.scala
```

This builds the app and serves it on <http://localhost:8080>. To build the executable to deploy:

```bash
snunit package Hello.scala -o hello
./hello
```

With sbt you don't need the CLI: there is an
[`sbt-snunit` plugin](https://lolgab.github.io/snunit/build-tools.html).

## Changelog

See [CHANGELOG.md](./CHANGELOG.md).

## License

Apache 2.0, see [LICENSE](./LICENSE).
