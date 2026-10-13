import snunit.*

@main
def run =
  SyncServerBuilder
    .setRequestHandler(req => req.send(StatusCode.OK, "Hello world!\n", Headers("Content-Type" -> "text/plain")))
    .build()
    .listen()
