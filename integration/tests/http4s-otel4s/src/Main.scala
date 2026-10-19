package snunit.tests

import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import snunit.otel4s.Otel4sHttp4s
import snunit.otel4s.Otel4sSdk

object Http4sOtel4s extends snunit.Http4sApp {
  object NameParam extends QueryParamDecoderMatcher[String]("name")

  def routes = for {
    otel4s <- Otel4sSdk.resource
    tracer <- Resource.eval(otel4s.tracerProvider.get("http4s-otel4s"))
  } yield Otel4sHttp4s.middleware(tracer)(
    HttpRoutes
      .of[IO] { case GET -> Root / "hello" :? NameParam(name) =>
        tracer.span("greet").surround(Ok(s"Hello $name!"))
      }
      .orNotFound
  )
}
