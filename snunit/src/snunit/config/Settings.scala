package snunit.config

import scala.concurrent.duration.FiniteDuration

/** A compression method. */
final case class Compressor(encoding: String, level: Option[Int] = None, minLength: Option[Int] = None) {
  private[config] def toJson: Json = Json.obj { o =>
    o.put("encoding", Json.Str(encoding))
    o.put("level", level)(Json.int(_))
    o.put("min_length", minLength)(Json.int(_))
  }
}

/** Compression of the responses. */
final case class Compression(
    /** MIME type patterns that are compressed. */
    types: Seq[String] = Nil,
    compressors: Seq[Compressor] = Nil
) {
  private[config] def toJson: Json = Json.obj { o =>
    o.putAll("types", types)
    if (compressors.nonEmpty) o.put("compressors", Json.Arr(compressors.map(_.toJson)))
  }
}

/** Websocket connections. */
final case class WebSocketSettings(
    readTimeout: Option[FiniteDuration] = None,
    keepaliveInterval: Option[FiniteDuration] = None,
    /** Maximum size in bytes of a frame. */
    maxFrameSize: Option[Long] = None
) {
  private[config] def toJson: Json = Json.obj { o =>
    o.put("read_timeout", readTimeout)(Json.seconds)
    o.put("keepalive_interval", keepaliveInterval)(Json.seconds)
    o.put("max_frame_size", maxFrameSize)(Json.int(_))
  }
}

/** The HTTP settings (`/config/settings/http`) of FreeUnit. Unset values keep the FreeUnit default. */
final case class HttpSettings(
    headerReadTimeout: Option[FiniteDuration] = None,
    bodyReadTimeout: Option[FiniteDuration] = None,
    sendTimeout: Option[FiniteDuration] = None,
    /** Maximum time between requests of a keep-alive connection. */
    idleTimeout: Option[FiniteDuration] = None,
    /** Minimum rate, in bytes per second, at which the body of a request is received. */
    bodyMinRate: Option[Long] = None,
    /** Minimum rate, in bytes per second, at which a response is sent. */
    sendMinRate: Option[Long] = None,
    largeHeaderBufferSize: Option[Long] = None,
    largeHeaderBuffers: Option[Int] = None,
    /** Maximum size in bytes of the body of a request. Larger requests are rejected by FreeUnit with 413. */
    maxBodySize: Option[Long] = None,
    /** Size in bytes of the memory buffer used to receive the body. A larger body is buffered in a temporary file. */
    bodyBufferSize: Option[Long] = None,
    /** Directory of the temporary files used for bodies larger than `bodyBufferSize`. */
    bodyTempPath: Option[String] = None,
    discardUnsafeFields: Option[Boolean] = None,
    logRoute: Option[Boolean] = None,
    serverVersion: Option[Boolean] = None,
    chunkedTransform: Option[Boolean] = None,
    webSocket: Option[WebSocketSettings] = None,
    compression: Option[Compression] = None,
    /** Extra MIME types of static files: type to file extensions. */
    staticMimeTypes: Map[String, Seq[String]] = Map.empty
) {
  private[config] def toJson: Json = Json.obj { o =>
    o.put("header_read_timeout", headerReadTimeout)(Json.seconds)
    o.put("body_read_timeout", bodyReadTimeout)(Json.seconds)
    o.put("send_timeout", sendTimeout)(Json.seconds)
    o.put("idle_timeout", idleTimeout)(Json.seconds)
    o.put("body_min_rate", bodyMinRate)(Json.int(_))
    o.put("send_min_rate", sendMinRate)(Json.int(_))
    o.put("large_header_buffer_size", largeHeaderBufferSize)(Json.int(_))
    o.put("large_header_buffers", largeHeaderBuffers)(Json.int(_))
    o.put("max_body_size", maxBodySize)(Json.int(_))
    o.put("body_buffer_size", bodyBufferSize)(Json.int(_))
    o.put("body_temp_path", bodyTempPath)(Json.Str(_))
    o.put("discard_unsafe_fields", discardUnsafeFields)(Json.Bool(_))
    o.put("log_route", logRoute)(Json.Bool(_))
    o.put("server_version", serverVersion)(Json.Bool(_))
    o.put("chunked_transform", chunkedTransform)(Json.Bool(_))
    o.put("websocket", webSocket)(_.toJson)
    o.put("compression", compression)(_.toJson)
    if (staticMimeTypes.nonEmpty)
      o.put(
        "static",
        Json.obj(
          _.put(
            "mime_types",
            Json.Obj(
              staticMimeTypes.toSeq.sortBy(_._1).map((mimeType, extensions) => mimeType -> Json.oneOrMany(extensions))
            )
          )
        )
      )
  }
}

/** OpenTelemetry tracing. */
final case class Telemetry(
    endpoint: String,
    batchSize: Option[Int] = None,
    protocol: Option[String] = None,
    /** Fraction of the requests traced, between 0 and 1. */
    samplingRatio: Option[Double] = None
) {
  private[config] def toJson: Json = Json.obj { o =>
    o.put("endpoint", Json.Str(endpoint))
    o.put("batch_size", batchSize)(Json.int(_))
    o.put("protocol", protocol)(Json.Str(_))
    o.put("sampling_ratio", samplingRatio)(r => Json.Num(r.toString))
  }
}

/** The access log. */
final case class AccessLog(
    path: String,
    format: Option[AccessLog.Format] = None,
    /** Only the requests for which this condition is true are logged. */
    condition: Option[String] = None
) {
  private[config] def toJson: Json = Json.obj { o =>
    o.put("path", Json.Str(path))
    o.put("format", format)(_.toJson)
    o.put("if", condition)(Json.Str(_))
  }
}

object AccessLog {
  enum Format {

    /** A line of text with variables, for example `$remote_addr $status`. */
    case Text(pattern: String)

    /** A JSON object per request: field name to text with variables. */
    case Fields(fields: Map[String, String])

    private[config] def toJson: Json = this match {
      case Text(pattern)  => Json.Str(pattern)
      case Fields(fields) => Json.stringMap(fields)
    }
  }
}
