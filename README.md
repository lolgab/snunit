# SNUnit: Scala Native HTTP server based on NGINX Unit

```scala
import snunit._
object HelloWorldExample {
  def main(args: Array[String]): Unit = {
    SyncServerBuilder
      .setRequestHandler(req =>
        req.send(
          statusCode = StatusCode.OK,
          content = s"Hello world!\n",
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

Further informations can be found in `unitd` logs in the running terminal.

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
