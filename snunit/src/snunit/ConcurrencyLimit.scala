package snunit

import snunit.unsafe.*

import scala.scalanative.unsafe.Ptr

/** Limits the requests that a process runs at the same time.
  *
  * FreeUnit puts the requests in a queue shared by the processes of the application, and a process takes one when it
  * reads the shared port. When the limit is reached the event loops stop reading the shared port, so the next requests
  * wait in the queue, where another process can take them, until a running request is done. A request that waits longer
  * than `limits.timeout` of the application gets 503.
  *
  * A request counts from the request handler to `nxt_unit_request_done`. A websocket counts until it is closed.
  *
  * libunit holds the first request from each thread of the router until it has the port to reply to that thread, and
  * calls the request handler later. That request isn't counted while it waits, so the first requests of a process can
  * go over the limit, by at most one per thread of the router.
  */
private[snunit] object ConcurrencyLimit {
  @volatile private var limit = 0

  // The running requests, by pointer: a request can be done twice (the application closes a websocket and then
  // libunit calls the close handler), and it must be counted once
  private val running = new java.util.HashSet[Long]

  @volatile private var count = 0

  @volatile private var onResume: () => Unit = () => ()

  /** `None` for no limit. */
  def setLimit(limit: Option[Int]): Unit = {
    require(limit.forall(_ > 0), s"the concurrency limit must be positive, got ${limit.get}")
    this.limit = limit.getOrElse(0)
  }

  /** Called when the shared port can be read again: the event loop has to read it, its notifications were not read. */
  def setOnResume(onResume: () => Unit): Unit = this.onResume = onResume

  /** Whether the event loop can read `port`: the ports that are not shared are always read. */
  def canRead(port: nxt_unit_port_t_*): Boolean =
    port.id.id != NXT_UNIT_SHARED_PORT_ID || limit == 0 || count < limit

  def acquire(req: nxt_unit_request_info_t_*): Unit = if (limit > 0) running.synchronized {
    if (running.add(id(req))) count += 1
  }

  def release(req: nxt_unit_request_info_t_*): Unit = if (limit > 0) {
    val resume = running.synchronized {
      running.remove(id(req)) && {
        count -= 1
        count == limit - 1
      }
    }
    if (resume) onResume()
  }

  private inline def id(req: nxt_unit_request_info_t_*): Long =
    scala.scalanative.runtime.Intrinsics
      .castRawPtrToLong(scala.scalanative.runtime.toRawPtr(req.asInstanceOf[Ptr[Byte]]))
}
