package snunit

export HeadersWrapper.*
object HeadersWrapper:
  opaque type Headers = Array[String]

  object Headers:
    inline def apply(size: Int): Headers = new Array[String](size * 2)
    inline def empty: Headers = apply(0)

    def apply(pairs: (String, String)*): Headers = apply(pairs, _._1, _._2)

    inline def apply[T](seq: Seq[T], inline getName: T => String, inline getValue: T => String): Headers = {
      seq match {
        case s: IndexedSeq[T] =>
          val len = s.length
          val headers = Headers(len)
          var i = 0
          while(i < len) {
            val elem: T = s(i)
            headers.updateName(i, getName(elem))
            headers.updateValue(i, getValue(elem))
            i += 1
          }
          headers

        case s =>
          val builder = Array.newBuilder[String]
          for (elem <- s) {
            builder += getName(elem)
            builder += getValue(elem)
          }
          builder.result()
      }
    }
    // TODO: Map[String, String] should have a different type since it
    // drops duplicated headers. It needs a revamp and so it does
    // the undertow implementation
    def apply(map: collection.Map[String, String]): Headers = {
      val builder = Array.newBuilder[String]
      for ((name, value) <- map) {
        builder += name
        builder += value
      }

      builder.result()
    }

  extension (headers: Headers)
    private def underlying: Array[String] = headers

    inline def length: Int = underlying.length / 2

    inline def name(i: Int): String = underlying.apply(i * 2)
    inline def updateName(i: Int, newName: String): Unit = underlying.update(i * 2, newName)
    inline def value(i: Int): String = underlying.apply(i * 2 + 1)
    inline def updateValue(i: Int, newValue: String): Unit = underlying.update(i * 2 + 1, newValue)

    def fieldsLength: Int = {
      var i = 0
      var res = 0
      while (i < underlying.length) {
        res += underlying(i).length
        i += 1
      }
      res
    }

    inline def foreach(inline f: (String, String) => Unit): Unit = {
      var i = 0
      while (i < underlying.length / 2) {
        val n = name(i)
        val v = value(i)

        f(n, v)
        i += 1
      }
    }

    // TODO: toMap should have a different signature since it
    // drops duplicated headers. It needs a revamp and so it does
    // the undertow implementation
    def toMap: Map[String, String] = {
      val builder = Map.newBuilder[String, String]
      var i = 0
      while(i < length) {
        builder += name(i) -> value(i)
        i += 1
      }
      builder.result()
    }
