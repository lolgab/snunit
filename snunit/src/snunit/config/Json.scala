package snunit.config

import scala.concurrent.duration.FiniteDuration

/** Minimal JSON tree, enough to render the FreeUnit configuration. */
private[config] enum Json {
  case Obj(fields: Seq[(String, Json)])
  case Arr(items: Seq[Json])
  case Str(value: String)
  case Num(value: String)
  case Bool(value: Boolean)

  def render: String = {
    val sb = new java.lang.StringBuilder
    Json.write(this, sb)
    sb.toString
  }
}

private[config] object Json {
  def int(value: Long): Json = Num(value.toString)
  def seconds(value: FiniteDuration): Json = Num(value.toSeconds.toString)
  def strings(values: Seq[String]): Json = Arr(values.map(Str(_)))

  /** A single string stays a string, anything else becomes an array: FreeUnit accepts both for patterns. */
  def oneOrMany(values: Seq[String]): Json = values match {
    case Seq(single) => Str(single)
    case _           => strings(values)
  }

  def stringMap(values: Map[String, String]): Json = Obj(values.toSeq.sortBy(_._1).map((k, v) => k -> Str(v)))

  /** Builds an object skipping the absent (`None`) and empty values. */
  final class ObjBuilder {
    private val fields = Seq.newBuilder[(String, Json)]
    def put(name: String, value: Json): this.type = { fields += name -> value; this }
    def put[A](name: String, value: Option[A])(render: A => Json): this.type = {
      value.foreach(v => fields += name -> render(v))
      this
    }
    def putAll(name: String, values: Seq[String]): this.type =
      if (values.isEmpty) this else put(name, oneOrMany(values))
    def putMap(name: String, values: Map[String, String]): this.type =
      if (values.isEmpty) this else put(name, stringMap(values))
    def result: Obj = Obj(fields.result())
  }

  def obj(f: ObjBuilder => Unit): Obj = {
    val builder = new ObjBuilder
    f(builder)
    builder.result
  }

  private def writeString(value: String, sb: java.lang.StringBuilder): Unit = {
    sb.append('"')
    var i = 0
    while (i < value.length) {
      val c = value.charAt(i)
      c match {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case c if c < ' ' =>
          sb.append("\\u").append(String.format("%04x", Integer.valueOf(c.toInt)))
        case c => sb.append(c)
      }
      i += 1
    }
    sb.append('"')
  }

  private def write(json: Json, sb: java.lang.StringBuilder): Unit = json match {
    case Str(value)  => writeString(value, sb)
    case Num(value)  => sb.append(value)
    case Bool(value) => sb.append(value)
    case Arr(items) =>
      sb.append('[')
      items.zipWithIndex.foreach { (item, i) =>
        if (i > 0) sb.append(',')
        write(item, sb)
      }
      sb.append(']')
    case Obj(fields) =>
      sb.append('{')
      fields.zipWithIndex.foreach { case ((name, value), i) =>
        if (i > 0) sb.append(',')
        writeString(name, sb)
        sb.append(':')
        write(value, sb)
      }
      sb.append('}')
  }
}
