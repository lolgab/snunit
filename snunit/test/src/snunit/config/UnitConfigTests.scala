package snunit.config

import utest.*
import scala.concurrent.duration.*

object UnitConfigTests extends TestSuite {
  val tests = Tests {
    test("default") {
      UnitConfig().toJson("/bin/app") ==>
        """{"listeners":{"*:8080":{"pass":"applications/app"}},"applications":{"app":{"type":"external","executable":"/bin/app"}}}"""
    }
    test("portAndProcesses") {
      UnitConfig().withPort(9000).withProcesses(4).toJson("/bin/app") ==>
        """{"listeners":{"*:9000":{"pass":"applications/app"}},"applications":{"app":{"type":"external","executable":"/bin/app","processes":4}}}"""
    }
    test("executableIsEscaped") {
      UnitConfig().toJson("/tmp/a \"b\"\\c\n").contains("\"executable\":\"/tmp/a \\\"b\\\"\\\\c\\n\"") ==> true
    }
    test("httpSettings") {
      val config = UnitConfig(
        http = HttpSettings(
          maxBodySize = Some(1024),
          idleTimeout = Some(2.minutes),
          discardUnsafeFields = Some(false),
          webSocket = Some(WebSocketSettings(maxFrameSize = Some(4096))),
          staticMimeTypes = Map("text/x-foo" -> Seq(".foo"))
        ),
        listenThreads = Some(2)
      )
      config.toJson("/bin/app") ==>
        """{"listeners":{"*:8080":{"pass":"applications/app"}},"applications":{"app":{"type":"external","executable":"/bin/app"}},"settings":{"listen_threads":2,"http":{"idle_timeout":120,"max_body_size":1024,"discard_unsafe_fields":false,"websocket":{"max_frame_size":4096},"static":{"mime_types":{"text/x-foo":".foo"}}}}}"""
    }
    test("application") {
      val application = Application(
        arguments = Seq("--a"),
        environment = Map("B" -> "2", "A" -> "1"),
        user = Some("nobody"),
        processes = Some(Processes.Dynamic(max = 8, spare = 2, idleTimeout = Some(10.seconds))),
        limits = Some(Limits(requests = Some(100), timeout = Some(5.seconds))),
        isolation = Some(Isolation(namespaces = Some(Namespaces(pid = Some(true))), uidMap = Seq(IdMap(0, 1000, 1))))
      )
      application.toJson("/bin/app").render ==>
        """{"type":"external","executable":"/bin/app","arguments":["--a"],"environment":{"A":"1","B":"2"},"user":"nobody","processes":{"max":8,"spare":2,"idle_timeout":10},"limits":{"requests":100,"timeout":5},"isolation":{"namespaces":{"pid":true},"uidmap":[{"container":0,"host":1000,"size":1}]}}"""
    }
    test("routes") {
      val config = UnitConfig(
        listeners = Seq(Listener(pass = Pass.Route("main"))),
        routes = Map(
          "main" -> Seq(
            RouteStep(
              Action.Share(Seq("/www/$uri"), fallback = Some(Action.Return(404))),
              Match(uri = Seq("/static/*"), method = Seq("GET", "HEAD"), headers = Seq(Map("Host" -> Seq("a.com"))))
            ),
            RouteStep(Action.Proxy("127.0.0.1:9000"), Match(uri = Seq("/api/*"))),
            RouteStep(Action.PassTo())
          )
        )
      )
      config.toJson("/bin/app") ==>
        """{"listeners":{"*:8080":{"pass":"routes/main"}},"applications":{"app":{"type":"external","executable":"/bin/app"}},"routes":{"main":[{"match":{"method":["GET","HEAD"],"uri":"/static/*","headers":{"Host":"a.com"}},"action":{"share":"/www/$uri","fallback":{"return":404}}},{"match":{"uri":"/api/*"},"action":{"proxy":"127.0.0.1:9000"}},{"action":{"pass":"applications/app"}}]}}"""
    }
    test("tls") {
      val config = UnitConfig(
        listeners =
          Seq(Listener.port(8443).copy(tls = Some(Tls(Seq("cert"), confCommands = Map("MinProtocol" -> "TLSv1.2"))))),
        certificates = Map("cert" -> "PEM")
      )
      config.toJson("/bin/app") ==>
        """{"listeners":{"*:8443":{"pass":"applications/app","tls":{"certificate":"cert","conf_commands":{"MinProtocol":"TLSv1.2"}}}},"applications":{"app":{"type":"external","executable":"/bin/app"}}}"""
    }
    test("upstreams") {
      val config = UnitConfig(
        listeners = Seq(Listener(pass = Pass.Upstream("pool"))),
        upstreams =
          Map("pool" -> Upstream(Seq(Upstream.Server("127.0.0.1:1", Some(2)), Upstream.Server("127.0.0.1:2"))))
      )
      config
        .toJson("/bin/app")
        .contains(""""upstreams":{"pool":{"servers":{"127.0.0.1:1":{"weight":2.0},"127.0.0.1:2":{}}}}""") ==> true
    }
    test("accessLogAndTelemetry") {
      val config = UnitConfig(
        accessLog = Some(AccessLog("/dev/stdout", Some(AccessLog.Format.Text("$status")))),
        telemetry = Some(Telemetry("http://localhost:4318", samplingRatio = Some(0.5)))
      )
      val json = config.toJson("/bin/app")
      json.contains(""""access_log":{"path":"/dev/stdout","format":"$status"}""") ==> true
      json.contains(""""telemetry":{"endpoint":"http://localhost:4318","sampling_ratio":0.5}""") ==> true
    }
    test("validation") {
      def invalid(config: UnitConfig): Unit = assertThrows[IllegalArgumentException] { config.toJson("/bin/app") }
      invalid(UnitConfig(listeners = Nil))
      invalid(UnitConfig(listeners = Seq(Listener(pass = Pass.Route("missing")))))
      invalid(UnitConfig(listeners = Seq(Listener(pass = Pass.Upstream("missing")))))
      invalid(UnitConfig(listeners = Seq(Listener(tls = Some(Tls(Seq("missing")))))))
      invalid(UnitConfig(listeners = Seq(Listener(), Listener())))
      invalid(UnitConfig(certificates = Map("../escape" -> "PEM")))
      invalid(UnitConfig(routes = Map("r" -> Seq(RouteStep(Action.PassTo(Pass.Route("missing")))))))
    }
  }
}
