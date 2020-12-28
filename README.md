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

## Middlewares

SNUnit builder pattern allows to implement middlewares. Using middlewares
it is possible to handle only some requests and skip others letting other
layers to handle them.
To pass a request to the next layer you need to call the `next()` method.
Example:

```scala
AsyncServerBuilder()
  .withRequestHandler(req => {
    if(req.method == Method.GET) {
      // handle GET requests
    } else {
      req.next() // pass other requests to successive layers
    }
  })
```

Since ServerBuilders are immutable you can implement middlewares as functions:

```scala
def myMiddleware[T <: ServerBuilder](builder: T): T = builder.
  withRequestHandler(req => {
    if(req.method == Method.PUT && headers.get("Content-Type").contains("application/json")) {
      // handle only PUTs with json content
    } else {
      req.next() // pass other requests to successive layers
    }
  })

myMiddleware(AsyncServerBuilder()).build()
```

You can even implement them as extension methods:

```scala
implicit class ServerBuilderOps[T <: ServerBuilder](private val builder: T) extends AnyVal {
  def with404: T = builder.withRequestHandler(req => {
    req.send(StatusCode.NotFound, "Not found", Seq.empty)
  })
}

AsyncServerBuilder()
  .withRequestHandler(req => {
    if(req.method == GET && req.path == "/") {
      Future(req.send(200, "Hello world!", Seq("Content-Type" -> "text/plain")))
    } else req.next()
  })
  .with404
  .build()
```

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
