---
title: Getting started
---

## Install

You need [scalino](https://github.com/lolgab/scalino), which `snunit` uses to build, and
the `snunit` command line tool:

```bash
curl -fsSL https://raw.githubusercontent.com/lolgab/snunit/main/install.sh | bash
```

The script puts `snunit` in `~/.local/bin` (set `INSTALL_DIR` to change it). If you prefer, download the
archive for your platform from the [releases](https://github.com/lolgab/snunit/releases) tagged `cli-v*`.

The CLI has its own releases and doesn't add SNUnit to your program. The `//> using dep` line in
your code decides which version of the library you get.

If you use sbt you don't need any of this, see [sbt and other build tools](build-tools.html).

## Run

Save this as `Hello.scala`. You don't need a build file, the `//> using dep` line is enough.

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

```bash
snunit run Hello.scala
```

This builds the app and serves it on <http://localhost:8080>. With `-w` it is rebuilt and
restarted on every change:

```bash
snunit run -w Hello.scala
```

`snunit` forwards the arguments it doesn't know to `scalino`, so `//> using` directives and
the usual flags work. Program arguments go after `--`:

```bash
snunit run Hello.scala -- --my-flag
```

The first time, `snunit` downloads the prebuilt FreeUnit for your platform and caches it
in `~/.cache/snunit`.

## Package

To build the executable to deploy somewhere else:

```bash
snunit package Hello.scala -o hello
./hello
```

The executable contains `unitd`. [How it works](how-it-works.html) explains what happens when you start it.

## Scala.js client

The server is a Scala Native program, so with scalino's Scala.js support you get a frontend without a JVM.
In the server, declare the client (a Scala.js project) and the sources shared by both sides:

```scala
//> using jsClient ../client
//> using shared ../shared
```

`snunit run` builds the client and embeds it in the executable as the resource `/client/main.js`.
Serve it from a handler with `getClass.getResourceAsStream("/client/main.js")`.
With `--watch` the app is also rebuilt when the client or the shared sources change.

The client is linked with fastLinkJS, or with fullLinkJS when the server is built with a `release-*`
`--native-mode`. `--js-mode` overrides this.

If you prefer to serve the client with FreeUnit's static files (a `share` route), add
`//> using jsClientOut ../client-dist` and nothing is embedded. scalino has a complete example in
[`examples/client-server`](https://github.com/lolgab/scalino/tree/main/examples/client-server).
