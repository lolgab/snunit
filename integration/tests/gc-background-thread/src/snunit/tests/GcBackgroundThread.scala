package snunit.tests

import java.util.concurrent.atomic.AtomicInteger
import snunit.*

/** A daemon thread allocates and runs the GC while the main thread waits in `listen()` and in the request handler. The
  * GC has to stop the main thread: if it is considered running while it waits for unit, the GC aborts after 60 seconds.
  * The response is the number of the request, so that a restart of the application (the abort) can be noticed.
  */
object GcBackgroundThread {
  private val requests = new AtomicInteger

  def main(args: Array[String]): Unit = {
    val background = new Thread(() => {
      while (true) {
        val garbage = Array.fill(1 << 18)(new Object)
        System.gc()
        if (garbage.isEmpty) println()
        Thread.sleep(100)
      }
    })
    background.setDaemon(true)
    background.start()

    SyncServerBuilder
      .setRequestHandler(req => {
        // Allocates and collects in the handler, a callback of unit
        val garbage = Array.fill(1 << 16)(new Object)
        System.gc()
        if (garbage.isEmpty) println()
        req.send(
          statusCode = StatusCode.OK,
          content = s"${requests.incrementAndGet()}\n",
          headers = Headers("Content-Type" -> "text/plain")
        )
      })
      .build()
      .listen()
  }
}
