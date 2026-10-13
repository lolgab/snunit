package snunit.launcher

import java.io.{IOException, RandomAccessFile}
import java.net.{InetSocketAddress, Socket, URI}
import java.nio.file.{Files, Paths}
import snunit.config.{Json, Logs}

/** A plain HTTP POST with short timeouts. Returns true if the server answered with a 2xx. */
private[launcher] object HttpPost {
  def apply(host: String, port: Int, path: String, contentType: String, body: Array[Byte]): Boolean = {
    var socket: Socket = null
    try {
      socket = new Socket()
      socket.connect(new InetSocketAddress(host, port), 1000)
      socket.setSoTimeout(1000)
      val out = socket.getOutputStream
      out.write(
        s"POST $path HTTP/1.1\r\nHost: $host:$port\r\nContent-Type: $contentType\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n"
          .getBytes("UTF-8")
      )
      out.write(body)
      out.flush()
      val buffer = new Array[Byte](32)
      val read = socket.getInputStream.read(buffer)
      read > 9 && buffer(9) == '2'.toByte // "HTTP/1.1 2xx"
    } catch {
      case _: IOException => false // The receiver may not be up yet.
    } finally if (socket != null) socket.close()
  }
}

/** Follows log files and pushes the new lines to Loki (or a server with the same push protocol). Like
  * [[MetricsExporter]] it has no thread: the launcher calls [[tick]] from its supervision loop.
  *
  * @param files
  *   source name and path of the files to follow. The files are read from their start
  */
private[launcher] final class LogShipper(config: Logs, serviceName: String, files: Seq[(String, String)]) {
  private final class Tail(val source: String, val path: String) {
    var offset = 0L
    var partial = new java.io.ByteArrayOutputStream
  }
  private val tails = files.map((source, path) => new Tail(source, path))
  private val (host, port, basePath) = {
    val uri = new URI(config.url)
    (uri.getHost, if (uri.getPort > 0) uri.getPort else 80, uri.getPath.stripSuffix("/"))
  }
  private val pending = new java.util.ArrayDeque[LogShipper.Line]
  private var nextPushAt = 0L
  private var dropped = 0L

  /** Reads what was appended to the files and pushes the pending lines if it is time to (or if `flush`). */
  def tick(flush: Boolean = false): Unit = {
    tails.foreach(read)
    val now = System.nanoTime()
    if (!pending.isEmpty && (flush || now - nextPushAt >= 0)) {
      nextPushAt = now + LogShipper.PushIntervalNanos
      push()
    }
  }

  private def read(tail: Tail): Unit = {
    val file = Paths.get(tail.path)
    if (Files.isRegularFile(file) && Files.size(file) > tail.offset) {
      val raf = new RandomAccessFile(tail.path, "r")
      try {
        raf.seek(tail.offset)
        val bytes = new Array[Byte](math.min(Files.size(file) - tail.offset, 1 << 20).toInt)
        val n = raf.read(bytes)
        if (n > 0) {
          tail.offset += n
          var start = 0
          var i = 0
          while (i < n) {
            if (bytes(i) == '\n'.toByte) {
              tail.partial.write(bytes, start, i - start)
              line(tail, new String(tail.partial.toByteArray, "UTF-8"))
              tail.partial.reset()
              start = i + 1
            }
            i += 1
          }
          tail.partial.write(bytes, start, n - start)
        }
      } catch { case _: IOException => () }
      finally raf.close()
    }
  }

  // The pushes go through FreeUnit when Loki is one of its applications: logging them would log the logging, forever.
  private val ownRequest = s""""url.path":"$basePath${config.pushPath}""""

  private def line(tail: Tail, text: String): Unit = {
    val ownPush = tail.source == "access" && text.contains(ownRequest)
    if (config.echo && !ownPush) println(text)
    if (text.nonEmpty && !ownPush) {
      if (pending.size >= config.maxBuffered) { pending.removeFirst(); dropped += 1 }
      pending.addLast(LogShipper.Line(tail.source, System.currentTimeMillis() * 1000000L, text))
    }
  }

  private def push(): Unit = {
    val batch = new java.util.ArrayList[LogShipper.Line](pending)
    val lines = Seq.from(scala.jdk.CollectionConverters.ListHasAsScala(batch).asScala)
    val body = LogShipper.payload(lines, serviceName, config.labels).getBytes("UTF-8")
    if (HttpPost(host, port, basePath + config.pushPath, "application/json", body)) {
      var i = 0
      while (i < lines.size) { pending.removeFirst(); i += 1 }
    } // otherwise the lines stay for the next push
  }
}

private[launcher] object LogShipper {
  val PushIntervalNanos: Long = 1000L * 1000 * 1000

  final case class Line(source: String, timestampNanos: Long, text: String)

  /** A Loki push request: a stream per source. */
  def payload(lines: Seq[Line], serviceName: String, labels: Map[String, String]): String =
    Json
      .obj(
        _.put(
          "streams",
          Json.Arr(
            lines.groupBy(_.source).toSeq.sortBy(_._1).map { (source, group) =>
              val stream = Map("service_name" -> serviceName, "job" -> "snunit") ++ labels + ("source" -> source)
              Json.obj { o =>
                o.put("stream", Json.stringMap(stream))
                o.put(
                  "values",
                  Json.Arr(group.map(l => Json.Arr(Seq(Json.Str(l.timestampNanos.toString), Json.Str(l.text)))))
                )
              }
            }
          )
        )
      )
      .render
}
