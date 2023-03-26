# SNUnit: Scala Native HTTP server based on NGINX Unit

```scala
import snunit._
object HelloWorldExample {
  def main(args: Array[String]): Unit = {
    SyncServerBuilder
      .setRequestHandler(req =>
        req.send(
          statusCode = StatusCode.OK,
          content = "Hello world!\n",
          headers = Seq("Content-Type" -> "text/plain")
        )
      )
      .build()
      .listen()
  }
}
```

SNUnit is a Scala Native library to write HTTP server applications on top of
[NGINX Unit](https://unit.nginx.org/). It allows you to write both synchronous
and asynchronous web servers with automatic restart on crashes, automatic
load balancing of multiple processes, great performance and all the nice
[NGINX Unit features](http://unit.nginx.org/#key-features).

## Running your app

Once built your SNUnit binary, you need to deploy it to the `unitd` server.

You need to run `unitd` in a terminal with:

```bash
unitd --no-daemon --log /dev/stdout --control unix:control.sock
```

This will run `unitd` with a UNIX socket file named control.sock in your current directory.

Then, you need to create a json file with your configuration:

```json
{
  "listeners": {
    "*:8081": {
      "pass": "applications/myapp"
    }
  },
  "applications": {
    "myapp": {
      "type": "external",
      "executable": "snunit/binary/path"
    }
  }
}
```

Where `executable` is the binary path which can be absolute or relative
to the `unitd` working directory.

This configuration passes all requests sent to the port `8081` to the application `myapp`.

To know more about configuring NGINX Unit, refer to [its documentation](http://unit.nginx.org/configuration).

To deploy the setting you can use curl:

```bash
curl -X PUT --unix-socket control.sock -d @config.json localhost/config
```

If everything went right, you should see this response:

```json
{
  "success": "Reconfiguration done."
}
```

In case of problems, you will get a 4xx response like this:

```json
{
  "error": "Invalid configuration.",
  "detail": "Required parameter \"executable\" is missing."
}
```

Further information can be found in `unitd` logs in the running terminal.

## Sync and async support

SNUnit has two different server implementations.

With `SyncServerBuilder` you need to call `.listen()` to start listening.
It is a blocking operation so your process is stuck on listening and can't do
anything else while listening.
Moreover, all the request handlers need to respond directly and can't be implemented
using `Future`s or any other asyncronous mechanism since no `Future` will run, being
the process stuck on the `listen()` Unit event loop.
With `AsyncServerBuilder` the server is automatically scheduled to run either on the
[scala-native-loop](https://github.com/scala-native/scala-native-loop) event loop
(based on the libuv library) or [epollcat](https://github.com/armanbilge/epollcat) event
loop, based on epoll/kqueue.
This allows you to complete requests asyncronously using whatever mechanism you prefer.
A process can accept multiple requests concurrently, allowing great parallelism.
Add either `snunit-async-loop` or `snunit-async-epollcat` to decide what implementation
you want to use.

## Tapir support

SNUnit offers interpreters for [Tapir](https://tapir.softwaremill.com) server endpoints.
You can write all your application using Tapir and the convert your Tapir endpoints
with logic into a SNUnit `Handler`.

Currently three interpreters are available:
- `SNUnitIdServerInterpreter` which works best with `SyncServerHandler` for synchronous applications
  - You can find an example [in tests](./integration/tests/tapir-helloworld/src/Main.scala)
- `SNUnitFutureServerInterpreter` which requires `AsyncServerHandler` for asynchronous applications
  - You can find an example [in tests](./integration/tests/tapir-helloworld-future/src/Main.scala)
- An interpreter for cats hidden behind `snunit.tapir.SNUnitServerBuilder` in the `snunit-tapir-cats` artifact.
  - You can find an example [in tests](./integration/tests/tapir-helloworld-cats/src/Main.scala)

## Http4s support

SNUnit offers a server implementation for [http4s](https://http4s.org).
It is based on the [epollcat](https://github.com/armanbilge/epollcat) asynchronous event loop.

There are two ways you can build a http4s server.

### Automatic server creation

`snunit.Http4sApp` extends `epollcat.EpollApp` building the SNUnit server.

It exposes a `def routes: HttpApp[IO]` that you need to implement with your
server logic.

Here an example "Hello world" app:

```scala
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._

object app extends snunit.Http4sApp {
  def routes = HttpRoutes
    .of[IO] { case GET -> Root =>
      Ok("Hello from SNUnit Http4s!")
    }
    .orNotFound
}
```

### Manual server creation

If you want to have more control over the server creation, you can use the
`SNUnitServerBuilder` and manually use it.

For example, here you see it in combination with `epollcat.EpollApp`

```scala
package snunit.tests

import cats.effect._
import epollcat.EpollApp
import org.http4s._
import org.http4s.dsl.io._
import snunit.http4s._

object Http4sHelloWorld extends EpollApp.Simple {
  def helloWorldRoutes: HttpRoutes[IO] = {
    HttpRoutes.of[IO] { case GET -> Root =>
      Ok("Hello Http4s!")
    }
  }

  def run: IO[Unit] = {
    SNUnitServerBuilder
      .default[IO]
      .withHttpApp(helloWorldRoutes.orNotFound)
      .build
      .useForever
  }
}
```
