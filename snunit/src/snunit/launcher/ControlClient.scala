package snunit.launcher

import scala.scalanative.meta.LinktimeInfo
import scala.scalanative.posix.sys.socket
import scala.scalanative.posix.unistd
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

/** Minimal client of the `unitd` control API, which listens on a unix socket.
  *
  * It exists to drain the server on shutdown: the router closes the listeners and exits as soon as it is told to quit,
  * dropping the running requests. Removing the listeners first and waiting for the connections to finish avoids it.
  */
private[snunit] final class ControlClient(socketPath: String) {

  /** Removes all the listeners: new connections are refused, the running ones continue. */
  def removeListeners(): Boolean = request("DELETE", "/config/listeners").isDefined

  /** The configured listeners as JSON (`{}` if there are none), `None` if the server can't be asked. */
  def listeners(): Option[String] = request("GET", "/config/listeners").map(_.trim)

  /** Number of active client connections, `None` if the server can't be asked. */
  def activeConnections(): Option[Int] =
    request("GET", "/status").flatMap(body =>
      ControlClient.ActiveConnections.findFirstMatchIn(body).map(_.group(1).toInt)
    )

  /** The body of the response, `None` if the request failed. Never blocks for more than a couple of seconds. */
  private def request(method: String, path: String): Option[String] = Zone {
    val fd = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM, 0)
    if (fd < 0) None
    else
      try {
        setTimeout(fd, socket.SO_RCVTIMEO)
        setTimeout(fd, socket.SO_SNDTIMEO)
        if (connect(fd)) {
          val bytes = s"$method $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes("UTF-8")
          if (unistd.write(fd, bytes.atUnsafe(0), bytes.length.toCSize) < 0) None
          else {
            val out = new java.io.ByteArrayOutputStream
            val buffer = new Array[Byte](4096)
            var read = unistd.read(fd, buffer.atUnsafe(0), buffer.length.toCSize)
            while (read > 0) {
              out.write(buffer, 0, read.toInt)
              read = unistd.read(fd, buffer.atUnsafe(0), buffer.length.toCSize)
            }
            val response = new String(out.toByteArray, "UTF-8")
            // "HTTP/1.1 200 OK"
            if (read < 0 || !response.startsWith("HTTP/1.1 2")) None
            else Some(response.substring(response.indexOf("\r\n\r\n") + 4))
          }
        } else None
      } finally unistd.close(fd)
  }

  /** Builds a `sockaddr_un` by hand: the layout differs between macOS (length byte first) and Linux. */
  private def connect(fd: CInt)(using Zone): Boolean = {
    val path = socketPath.getBytes("UTF-8")
    // sun_path is 104 bytes on macOS, 108 on Linux.
    if (path.length >= 104) false
    else {
      val addressSize = 110
      val address = alloc[Byte](addressSize)
      val pathOffset = 2
      if (LinktimeInfo.isMac) {
        address(0) = (pathOffset + path.length + 1).toByte // sun_len
        address(1) = socket.AF_UNIX.toByte // sun_family
      } else {
        address(0) = socket.AF_UNIX.toByte // sun_family, little endian 16 bit
        address(1) = (socket.AF_UNIX >> 8).toByte
      }
      var i = 0
      while (i < path.length) { address(pathOffset + i) = path(i); i += 1 }
      socket.connect(fd, address.asInstanceOf[Ptr[socket.sockaddr]], (pathOffset + path.length + 1).toUInt) == 0
    }
  }

  /** Two seconds, as a `struct timeval` (16 bytes on all the platforms we run on: two 64 bit fields, the second
    * possibly padded).
    */
  private def setTimeout(fd: CInt, option: CInt)(using Zone): Unit = {
    val timeval = alloc[Long](2)
    timeval(0) = 2L
    timeval(1) = 0L
    socket.setsockopt(fd, socket.SOL_SOCKET, option, timeval.asInstanceOf[Ptr[Byte]], 16.toUInt)
  }
}

private[snunit] object ControlClient {
  private val ActiveConnections = """"connections"\s*:\s*\{[^}]*?"active"\s*:\s*(\d+)""".r
}
