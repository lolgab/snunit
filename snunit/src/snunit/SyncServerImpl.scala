package snunit

import snunit.unsafe._

import scala.scalanative.libc.{errno, stdlib}
import scala.scalanative.libc.string.strerror
import scala.scalanative.posix.fcntl.{F_SETFL, O_NONBLOCK, fcntl}
import scala.scalanative.posix.poll.*
import scala.scalanative.posix.pollOps.*
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

/** Runs the event loop of libunit, instead of `nxt_unit_run`.
  *
  * `nxt_unit_run` waits for the next message and calls the request handler in the same call. Scala Native doesn't
  * switch the thread back to `Managed` when foreign code calls a Scala callback, so if it was `@blocking` (`Unmanaged`)
  * the handler would run in that state, and if it wasn't, the thread would wait `Managed` and the GC of the other
  * threads would stall until it aborts after 60 seconds.
  *
  * Like an application with its own event loop, this waits for the ports of libunit with `poll` (`@blocking`) and then
  * reads them with `nxt_unit_process_port_msg`, that doesn't wait: the callbacks are called from `Managed` code.
  */
class SyncServerImpl private[snunit] (private val ctx: nxt_unit_ctx_t_*) extends SyncServer {
  def listen(): Unit = {
    while (SyncServerImpl.online) SyncServerImpl.runOnce()
    nxt_unit_done(ctx)
  }

  /** Waits up to 20 milliseconds for a message and processes what libunit can read. */
  def runOnce(): Unit = SyncServerImpl.runOnce()
  def done(): Unit = {
    nxt_unit_done(ctx)
  }
}

private[snunit] object SyncServerImpl {
  private val MaxPorts = 16
  private val PollTimeoutMillis = 20

  // The ports of libunit and the poll descriptors are in native memory and `runOnce` doesn't allocate: it runs
  // 50 times a second, and every time it returns from `poll` the thread is `Unmanaged` until it is `Managed` again
  private val contexts: Ptr[Ptr[Byte]] =
    stdlib.malloc(sizeof[Ptr[Byte]] * MaxPorts.toUSize).asInstanceOf[Ptr[Ptr[Byte]]]
  private val ports: Ptr[Ptr[Byte]] = stdlib.malloc(sizeof[Ptr[Byte]] * MaxPorts.toUSize).asInstanceOf[Ptr[Ptr[Byte]]]
  private val fds: Ptr[struct_pollfd] =
    stdlib.malloc(sizeof[struct_pollfd] * MaxPorts.toUSize).asInstanceOf[Ptr[struct_pollfd]]
  private var count = 0

  /** False when libunit asked to quit. */
  @volatile var online: Boolean = true

  private def indexOf(port: Ptr[Byte]): Int = {
    var i = 0
    while (i < count && ports(i) != port) i += 1
    if (i < count) i else -1
  }

  val add_port: add_port_t = add_port_t { (ctx: nxt_unit_ctx_t_*, port: nxt_unit_port_t_*) =>
    if (port.in_fd == -1 || indexOf(port.asInstanceOf[Ptr[Byte]]) != -1) NXT_UNIT_OK
    else if (count == MaxPorts) {
      nxt_unit_warn(ctx, "too many ports")
      NXT_UNIT_ERROR
    } else if (fcntl(port.in_fd, F_SETFL, O_NONBLOCK) == -1) {
      nxt_unit_warn(ctx, s"fcntl(${port.in_fd}, O_NONBLOCK) failed: ${fromCString(strerror(errno.errno))}")
      NXT_UNIT_ERROR
    } else {
      contexts(count) = ctx.asInstanceOf[Ptr[Byte]]
      ports(count) = port.asInstanceOf[Ptr[Byte]]
      count += 1
      NXT_UNIT_OK
    }
  }

  val remove_port: remove_port_t = remove_port_t { (_: nxt_unit_t_*, _: nxt_unit_ctx_t_*, port: nxt_unit_port_t_*) =>
    var i = indexOf(port.asInstanceOf[Ptr[Byte]])
    if (i != -1) {
      count -= 1
      while (i < count) {
        contexts(i) = contexts(i + 1)
        ports(i) = ports(i + 1)
        i += 1
      }
    }
  }

  val quit: quit_t = quit_t { (_: nxt_unit_ctx_t_*) => online = false }

  /** Waits for a message and processes what libunit can read. */
  def runOnce(): Unit = {
    val n = count
    var i = 0
    while (i < n) {
      val pollfd = fds + i
      pollfd.fd = ports(i).asInstanceOf[nxt_unit_port_t_*].in_fd
      pollfd.events = POLLIN.toShort
      pollfd.revents = 0
      i += 1
    }
    // The notifications of libunit are edge-triggered: a message can be queued without a new notification after the
    // ports were read, so this doesn't wait forever. Without ports it only waits.
    val readable = poll(fds, n.toUInt, if (n == 0) 1 else PollTimeoutMillis)
    // libunit doesn't read the shared port until the context is ready, which depends on messages from the other
    // ports, so all the ports are read. One message of each port at a time, like libunit does: reading a port until
    // it is empty would make the messages of the other ports wait while requests keep arriving.
    var progress = false
    var round = true
    while (round) {
      round = false
      i = 0
      while (i < count) {
        val ctx = contexts(i).asInstanceOf[nxt_unit_ctx_t_*]
        val port = ports(i).asInstanceOf[nxt_unit_port_t_*]
        if (nxt_unit_process_port_msg(ctx, port) == NXT_UNIT_OK) {
          round = true
          progress = true
        }
        i += 1
      }
    }
    // A port is readable but libunit can't read it yet: don't spin
    if (readable > 0 && !progress) poll(fds, 0.toUInt, 1)
  }
}
