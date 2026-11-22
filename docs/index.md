---
title: SNUnit
subtitle: Scala Native HTTP server based on FreeUnit
---

SNUnit is a Scala Native library to write HTTP server applications on top of
[FreeUnit](https://freeunit.org/), the community LTS fork of NGINX Unit.
You write the server in Scala and FreeUnit takes care of the processes: it starts them,
restarts them when they crash and balances the requests between them.

```scala
//> using dep com.github.lolgab::snunit::{{ site.snunit_version }}
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

An SNUnit application is a single executable. It embeds `unitd`, starts it with a
generated configuration and then runs itself as the application. There is
nothing to install besides the `snunit` command line tool.

Save the example as `Hello.scala` and run it with `snunit run Hello.scala`.
[Getting started](getting-started.html) explains how to install the tool.

## Modules

You can use SNUnit directly, as in the example above, or through the library you already use.

| Artifact | What it is |
|---|---|
| `snunit` | The core: `SyncServerBuilder` and the [configuration](configuration.html) API |
| `snunit-http4s0.23`, `snunit-http4s1` | [http4s](http4s.html) server, for http4s 0.23 and for the 1.0 milestones |
| `snunit-tapir` | Synchronous [Tapir](tapir.html) interpreter |
| `snunit-tapir-cats-effect` | [Tapir](tapir.html) interpreter for cats-effect |
| `snunit-cask` | [Cask](cask.html), built for Scala Native |
| `snunit-undertow` | The [Undertow](undertow.html) API, implemented with SNUnit |
| `snunit-endpoints4s` | Synchronous [endpoints4s](endpoints4s.html) interpreter |
| `snunit-async-cats-effect` | The cats-effect server the http4s and Tapir modules are built on |

Everything is published under `com.github.lolgab`. The latest release is {{ site.snunit_version }}.

## Requirements

- Scala 3
- Scala Native 0.5
- Linux or macOS, on x86_64 or aarch64

SNUnit is distributed under the Apache 2.0 license.

## Getting help

Found a bug, or something in these pages is not clear?
[Open an issue](https://github.com/lolgab/snunit/issues).
