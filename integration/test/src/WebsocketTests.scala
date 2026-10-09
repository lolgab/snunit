package snunit.test

import utest._
import scala.concurrent.Await
import scala.concurrent.duration.*
import sttp.client3._

object WebsocketTests extends TestSuite {
  val tests = Tests {
    test("hello-world") {
      withDeployedExample("websocket-echo") {
        for
          response <- request
            .get(websocketBaseUrl)
            .websocket()

          websocket = response.body

          _ <- websocket.send(Frame.Ping(Array.emptyByteArray))
          _ <- websocket.send(Frame.Text("Hello", false, None))
          _ <- websocket.send(Frame.Text("World", false, None))
          case Frame.Pong(_) <- websocket.receive()
          case Frame.Text(firstFrame, _, _) <- websocket.receive()
          case Frame.Text(secondFrame, _, _) <- websocket.receive()
          _ <- websocket.close()
        yield
          firstFrame ==> "Hello"
          secondFrame ==> "World"
      }
    }
    test("tapir") {
      withDeployedExample("tapir-websocket", readyUrl = uri"$baseUrl/echo") {
        for
          response <- request
            .get(uri"$websocketBaseUrl/echo")
            .websocket()

          websocket = response.body

          _ <- websocket.send(Frame.Text("Hello", true, None))
          _ <- websocket.send(Frame.Text("World", true, None))
          case Frame.Text(firstFrame, _, _) <- websocket.receive()
          case Frame.Text(secondFrame, _, _) <- websocket.receive()
          _ <- websocket.close()
        yield
          firstFrame ==> "Hello"
          secondFrame ==> "World"
      }
    }
    test("tapir-sync") {
      withDeployedExample("tapir-websocket-sync", readyUrl = uri"$baseUrl/echo") {
        for
          response <- request
            .get(uri"$websocketBaseUrl/echo")
            .websocket()

          websocket = response.body

          _ <- websocket.send(Frame.Text("Hello", true, None))
          _ <- websocket.send(Frame.Text("World", true, None))
          case Frame.Text(firstFrame, _, _) <- websocket.receive()
          case Frame.Text(secondFrame, _, _) <- websocket.receive()
          _ <- websocket.close()
        yield
          firstFrame ==> "Hello"
          secondFrame ==> "World"
      }
    }
    test("http4s") {
      withDeployedExampleHttp4s("http4s-websocket") {
        // the helper discards the result, so the future has to be awaited here
        Await.result(
          for
            response <- request
              .get(uri"$websocketBaseUrl/echo")
              .websocket()

            websocket = response.body

            _ <- websocket.send(Frame.Text("Hello", true, None))
            _ <- websocket.send(Frame.Text("World", true, None))
            case Frame.Text(firstFrame, _, _) <- websocket.receive()
            case Frame.Text(secondFrame, _, _) <- websocket.receive()
            _ <- websocket.close()
          yield
            firstFrame ==> "Hello"
            secondFrame ==> "World"
          ,
          30.seconds
        )
      }
    }
  }
}
