package snunit.launcher

import java.io.{BufferedReader, InputStreamReader}
import java.net.{InetAddress, InetSocketAddress, ServerSocket, Socket, SocketTimeoutException, URI}
import scala.concurrent.duration.*
import snunit.config.{Json, Observability}

/** A measurement of the server. Counters only increase, gauges go up and down. */
private[launcher] final case class Sample(
    name: String,
    help: String,
    isCounter: Boolean,
    value: Long,
    labels: Seq[(String, String)] = Nil
)

private[launcher] object Metrics {

  /** The status of FreeUnit, `GET /status`, as samples. */
  def fromStatus(status: String): Seq[Sample] = {
    val root = MiniJson.parse(status)
    def long(path: String*): Option[Long] = MiniJson.number(MiniJson.get(root, path*))
    def counter(name: String, help: String, path: String*) = long(path*).map(Sample(name, help, true, _))
    def gauge(name: String, help: String, path: String*) = long(path*).map(Sample(name, help, false, _))
    val general = Seq(
      counter("snunit_connections_accepted_total", "Connections accepted.", "connections", "accepted"),
      counter("snunit_connections_closed_total", "Connections closed.", "connections", "closed"),
      gauge("snunit_connections_active", "Connections with a request in progress.", "connections", "active"),
      gauge("snunit_connections_idle", "Idle keep-alive connections.", "connections", "idle"),
      counter("snunit_requests_total", "Requests received.", "requests", "total")
    ).flatten
    val applications = MiniJson.get(root, "applications") match {
      case Some(apps: Map[?, ?]) =>
        apps.asInstanceOf[Map[String, Any]].toSeq.sortBy(_._1).flatMap { (name, app) =>
          val application = "application" -> name
          def processes(state: String) =
            MiniJson
              .number(MiniJson.get(app, "processes", state))
              .map(
                Sample(
                  "snunit_application_processes",
                  "Application processes by state.",
                  false,
                  _,
                  Seq(application, "state" -> state)
                )
              )
          Seq("running", "starting", "idle").flatMap(processes) ++
            MiniJson
              .number(MiniJson.get(app, "requests", "active"))
              .map(
                Sample(
                  "snunit_application_requests_active",
                  "Requests being handled by the application.",
                  false,
                  _,
                  Seq(application)
                )
              )
        }
      case _ => Nil
    }
    general ++ applications
  }

  /** The Prometheus text exposition format. */
  def prometheus(samples: Seq[Sample]): String = {
    val sb = new java.lang.StringBuilder
    samples.groupBy(_.name).toSeq.sortBy(_._1).foreach { (name, group) =>
      val first = group.head
      sb.append("# HELP ").append(name).append(' ').append(first.help).append('\n')
      sb.append("# TYPE ").append(name).append(if (first.isCounter) " counter\n" else " gauge\n")
      group.foreach { s =>
        sb.append(name)
        if (s.labels.nonEmpty)
          sb.append(s.labels.map((k, v) => s"""$k="${escapeLabel(v)}"""").mkString("{", ",", "}"))
        sb.append(' ').append(s.value).append('\n')
      }
    }
    sb.toString
  }

  private def escapeLabel(value: String): String =
    value.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"")

  /** An OTLP/JSON `ExportMetricsServiceRequest`. */
  def otlp(samples: Seq[Sample], resource: Seq[(String, String)], startNanos: Long, nowNanos: Long): String = {
    def attributes(values: Seq[(String, String)]): Json =
      Json.Arr(
        values.map((k, v) =>
          Json.obj(_.put("key", Json.Str(k)).put("value", Json.obj(_.put("stringValue", Json.Str(v)))))
        )
      )
    def metric(name: String, group: Seq[Sample]): Json = {
      val first = group.head
      val points = Json.Arr(group.map { s =>
        Json.obj { o =>
          o.put("attributes", attributes(s.labels))
          o.put("startTimeUnixNano", Json.Str(startNanos.toString))
          o.put("timeUnixNano", Json.Str(nowNanos.toString))
          o.put("asInt", Json.Str(s.value.toString))
        }
      })
      Json.obj { o =>
        o.put("name", Json.Str(name))
        o.put("description", Json.Str(first.help))
        if (first.isCounter)
          o.put(
            "sum",
            Json.obj(
              _.put("dataPoints", points).put("aggregationTemporality", Json.int(2)).put("isMonotonic", Json.Bool(true))
            )
          )
        else o.put("gauge", Json.obj(_.put("dataPoints", points)))
      }
    }
    val metrics = samples.groupBy(_.name).toSeq.sortBy(_._1).map(metric)
    Json
      .obj(
        _.put(
          "resourceMetrics",
          Json.Arr(
            Seq(
              Json.obj(
                _.put("resource", Json.obj(_.put("attributes", attributes(resource))))
                  .put(
                    "scopeMetrics",
                    Json.Arr(
                      Seq(
                        Json.obj(
                          _.put("scope", Json.obj(_.put("name", Json.Str("snunit")))).put("metrics", Json.Arr(metrics))
                        )
                      )
                    )
                  )
              )
            )
          )
        )
      )
      .render
  }
}

/** Exposes the metrics of a running launcher: serves the Prometheus endpoint and pushes OTLP. It has no thread of its
  * own, the launcher gives it time with [[tick]] from its supervision loop. All the waits are short and bounded.
  *
  * @param collect
  *   the current samples
  */
private[launcher] final class MetricsExporter(config: Observability, collect: () => Seq[Sample]) {
  private val startNanos = System.currentTimeMillis() * 1000000L
  private var nextPushAt = System.nanoTime()
  private val server: Option[ServerSocket] = config.prometheus.flatMap { p =>
    try {
      val socket = new ServerSocket()
      socket.setReuseAddress(true)
      socket.bind(new InetSocketAddress(InetAddress.getByName(p.host), p.port))
      Some(socket)
    } catch {
      case e: java.io.IOException =>
        System.err.println(s"snunit: cannot serve the Prometheus metrics on ${p.host}:${p.port}: ${e.getMessage}")
        None
    }
  }
  private val otlp: Option[(String, Int, String)] = config.metricsInterval.map { _ =>
    val uri = new URI(config.otlpEndpoint)
    (uri.getHost, if (uri.getPort > 0) uri.getPort else 80, uri.getPath.stripSuffix("/") + "/v1/metrics")
  }

  /** Waits at most `wait` for a scrape, serves it, and pushes the metrics if it is time to. */
  def tick(wait: FiniteDuration, pushing: Boolean): Unit = {
    server match {
      case Some(s) => serve(s, wait)
      case None    => Thread.sleep(wait.toMillis)
    }
    if (pushing && otlp.isDefined && System.nanoTime() - nextPushAt >= 0) {
      nextPushAt = System.nanoTime() + config.metricsInterval.get.toNanos
      push()
    }
  }

  def close(): Unit = server.foreach(_.close())

  private def serve(s: ServerSocket, wait: FiniteDuration): Unit = {
    s.setSoTimeout(math.max(1L, wait.toMillis).toInt)
    try {
      val client = s.accept()
      try respond(client)
      catch { case _: java.io.IOException => () } // a client that goes away is not a problem
      finally client.close()
    } catch {
      // A signal interrupts the poll of accept: the launcher loop looks at it right away.
      case _: SocketTimeoutException   => ()
      case _: java.net.SocketException => Thread.sleep(wait.toMillis)
    }
  }

  private def respond(client: Socket): Unit = {
    client.setSoTimeout(500)
    val reader = new BufferedReader(new InputStreamReader(client.getInputStream, "UTF-8"))
    val requestLine = reader.readLine()
    val parts = if (requestLine == null) Array.empty[String] else requestLine.split(' ')
    val (status, body) =
      if (parts.length >= 2 && parts(0) == "GET" && parts(1).takeWhile(_ != '?') == "/metrics")
        ("200 OK", Metrics.prometheus(collect()))
      else ("404 Not Found", "Not found\n")
    val bytes = body.getBytes("UTF-8")
    val head =
      s"HTTP/1.1 $status\r\nContent-Type: text/plain; version=0.0.4; charset=utf-8\r\nContent-Length: ${bytes.length}\r\nConnection: close\r\n\r\n"
    val out = client.getOutputStream
    out.write(head.getBytes("UTF-8"))
    out.write(bytes)
    out.flush()
  }

  private def push(): Unit = otlp.foreach { (host, port, path) =>
    val body = Metrics
      .otlp(collect(), config.resource, startNanos, System.currentTimeMillis() * 1000000L)
      .getBytes("UTF-8")
    HttpPost(host, port, path, "application/json", body)
  }
}

/** Minimal JSON reader for the FreeUnit status: objects, numbers and strings. */
private[launcher] object MiniJson {
  def parse(text: String): Any = {
    var i = 0
    def skip(): Unit = while (i < text.length && text(i).isWhitespace) i += 1
    def string(): String = {
      i += 1 // opening quote
      val sb = new java.lang.StringBuilder
      while (text(i) != '"') {
        if (text(i) == '\\') { i += 1; sb.append(if (text(i) == 'n') '\n' else text(i)) }
        else sb.append(text(i))
        i += 1
      }
      i += 1
      sb.toString
    }
    def value(): Any = {
      skip()
      text(i) match {
        case '{' =>
          i += 1
          val fields = Map.newBuilder[String, Any]
          skip()
          if (text(i) == '}') i += 1
          else {
            var more = true
            while (more) {
              skip()
              val key = string()
              skip(); i += 1 // colon
              fields += key -> value()
              skip()
              if (text(i) == ',') i += 1 else { i += 1; more = false }
            }
          }
          fields.result()
        case '"' => string()
        case '[' =>
          i += 1
          val items = Seq.newBuilder[Any]
          skip()
          if (text(i) == ']') i += 1
          else {
            var more = true
            while (more) {
              items += value()
              skip()
              if (text(i) == ',') i += 1 else { i += 1; more = false }
            }
          }
          items.result()
        case _ =>
          val start = i
          while (i < text.length && !",}] \n\r\t".contains(text(i))) i += 1
          val token = text.substring(start, i)
          token.toLongOption.getOrElse(token)
      }
    }
    try value()
    catch { case _: IndexOutOfBoundsException => Map.empty[String, Any] }
  }

  def get(value: Any, path: String*): Option[Any] =
    path.foldLeft(Option(value)) {
      case (Some(m: Map[?, ?]), key) => m.asInstanceOf[Map[String, Any]].get(key)
      case _                         => None
    }

  def number(value: Option[Any]): Option[Long] = value.collect { case n: Long => n }
}
