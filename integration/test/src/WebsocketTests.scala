package snunit.test

import utest._
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
  }
}
