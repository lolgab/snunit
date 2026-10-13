package snunit.launcher

import utest.*

object MetricsTests extends TestSuite {
  private val status =
    """{"connections":{"accepted":7,"active":1,"idle":2,"closed":4},"requests":{"total":42},"applications":{"app":{"processes":{"running":3,"starting":0,"idle":1},"requests":{"active":1}}}}"""

  val tests = Tests {
    test("status") {
      val samples = Metrics.fromStatus(status)
      samples.find(_.name == "snunit_requests_total").map(_.value) ==> Some(42L)
      samples.find(_.name == "snunit_connections_active").map(_.value) ==> Some(1L)
      samples
        .find(s => s.name == "snunit_application_processes" && s.labels.contains("state" -> "running"))
        .map(_.value) ==> Some(3L)
      Metrics.fromStatus("not json") ==> Nil
    }
    test("prometheus") {
      val text = Metrics.prometheus(Metrics.fromStatus(status))
      assert(text.contains("# TYPE snunit_requests_total counter\nsnunit_requests_total 42\n"))
      assert(text.contains("# TYPE snunit_connections_active gauge\nsnunit_connections_active 1\n"))
      assert(text.contains("""snunit_application_processes{application="app",state="running"} 3"""))
    }
    test("otlp") {
      val json = Metrics.otlp(Metrics.fromStatus(status), Seq("service.name" -> "svc"), 1L, 2L)
      assert(json.contains(""""resource":{"attributes":[{"key":"service.name","value":{"stringValue":"svc"}}]}"""))
      assert(json.contains(""""name":"snunit_requests_total""""))
      assert(
        json.contains(
          """"sum":{"dataPoints":[{"attributes":[],"startTimeUnixNano":"1","timeUnixNano":"2","asInt":"42"}],"aggregationTemporality":2,"isMonotonic":true}"""
        )
      )
      assert(json.contains(""""gauge":{"dataPoints":"""))
    }

    test("lokiPayload") {
      val json = LogShipper.payload(
        Seq(LogShipper.Line("access", 5L, "a \"line\""), LogShipper.Line("unit", 6L, "u")),
        "svc",
        Map("env" -> "dev")
      )
      assert(json.contains(""""stream":{"env":"dev","job":"snunit","service_name":"svc","source":"access"}"""))
      assert(json.contains(""""values":[["5","a \"line\""]]"""))
      assert(json.contains(""""source":"unit"}"""))
    }
  }
}
