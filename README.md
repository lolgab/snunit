# SNUnit: Scala Native HTTP server based on NGINX Unit

```scala
import snunit._
object HelloWorldExample {
  def main(args: Array[String]): Unit = {
    val server = SyncServerBuilder()
      .withRequestHandler(req =>
        req.send(
          statusCode = StatusCode.OK,
          content = s"Hello world!\n",
          headers = Seq("Content-Type" -> "text/plain")
        )
      )
      .build()

    server.listen()
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

To know more about configuring NGINX Unit, refer to [their documentation](http://unit.nginx.org/configuration).

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
With `AsyncServerBuilder` the server is automatically scheduled to run on the
[scala-native-loop](https://github.com/scala-native/scala-native-loop) event loop
(based on the libuv library). This allows you to complete requests asyncronously
using whatever mechanism you prefer. A process can accept multiple requests concurrently,
allowing great parallelism.

## Routes support

SNUnit supports routes using the [trail](https://github.com/sparsetech/trail) Scala library.
You need to import the `snunit-routes` module and you can write handlers for specific routes:

```scala
import snunit._
import snunit.routes._
import trail._

val details  = Root / "details" / Arg[Int]
val userInfo = Root / "user" / Arg[String] & Param[Boolean]("show")

AsyncServerBuilder()
  .withRequestHandler(
    _.withMethod(Method.GET)
      .withRoute(details) { case (req, details) =>
        req.send(200, details, Seq.empty)
      }
  )
  .withRequestHandler(_.withRoute(userInfo) { case (req, (user, show)) =>
    req.send(200, s"User: $user, show: $show", Seq.empty)
  })
  .build()
```
