package snunit

import io.undertow.Undertow
import io.undertow.server._
import io.undertow.server.handlers.BlockingHandler
import io.undertow.util._

import java.nio.ByteBuffer
import scala.jdk.CollectionConverters._

object SyncServerBuilder {
  def build(handler: Handler): SyncServer = {
    val port = sys.env.get("SNUNIT_PORT").map(_.toInt).getOrElse(8080)
    new SyncServer {
      private val server = Undertow
        .builder()
        .addHttpListener(port, "127.0.0.1")
        .setHandler(new BlockingHandler(new HttpHandler() {
          def handleRequest(exchange: HttpServerExchange): Unit = {
            handler.handleRequest(new Request {
              def method: Method = methodOf(exchange.getRequestMethod())
              def target: String = {
                val q = query
                val querySuffix = if (q.nonEmpty) s"?$q" else q
                s"${exchange.getRequestURI()}$querySuffix"
              }
              def path: String = exchange
                .getRequestPath()
                .replace("%2F", "/")
                .replace("%2f", "/")
                .replace("%5C", "\\")
                .replace("%5c", "\\")
              def query: String = exchange
                .getQueryParameters()
                .asScala
                .flatMap { case (k, values) => values.asScala.map(v => s"$k=$v") }
                .mkString("&")
              def contentRaw: Array[Byte] = {
                exchange.startBlocking()
                val src = exchange.getInputStream()
                val available = src.available()
                if (available > 0) {
                  val buffer = new Array[Byte](available)
                  src.read(buffer)
                  src.close()
                  buffer
                } else new Array[Byte](0)
              }
              def headers: Seq[(String, String)] = {
                val builder = Seq.newBuilder[(String, String)]
                val it = exchange.getRequestHeaders().iterator()
                while (it.hasNext()) {
                  val headerValues = it.next()
                  val headerValuesIt = headerValues.iterator()
                  while (headerValuesIt.hasNext()) {
                    builder += headerValues.getHeaderName().toString() -> headerValuesIt.next()
                  }
                }
                builder.result()
              }
              def headerName(index: Int): String = headers(index)._1
              def headerNameUnsafe(index: Int): String = headers(index)._1
              def headerValue(index: Int): String = headers(index)._2
              def headerValueUnsafe(index: Int): String = headers(index)._2
              def headersLength: Int = headers.length
              def send(statusCode: StatusCode, contentRaw: Array[Byte], headers: Seq[(String, String)]): Unit = {
                exchange.setStatusCode(statusCode.value)
                val responseHeaders = exchange.getResponseHeaders()
                headers.foreach { case (key, value) =>
                  responseHeaders.put(new HttpString(key), value)
                }
                exchange.getResponseSender().send(ByteBuffer.wrap(contentRaw))
              }
            })
          }
        }))
        .build()

      def listen(): Unit = server.start()
    }
  }

  private def methodOf(name: HttpString): Method = {
    if (name.equalToString("GET")) Method.GET
    else if (name.equalToString("PUT")) Method.PUT
    else if (name.equalToString("POST")) Method.POST
    else if (name.equalToString("HEAD")) Method.HEAD
    else if (name.equalToString("PATCH")) Method.PATCH
    else if (name.equalToString("TRACE")) Method.TRACE
    else if (name.equalToString("DELETE")) Method.DELETE
    else if (name.equalToString("CONNECT")) Method.CONNECT
    else if (name.equalToString("OPTIONS")) Method.OPTIONS
    else new Method(name.toString())
  }
}
