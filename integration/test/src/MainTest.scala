import utest._

import TestUtils._

object MainTest extends TestSuite {
  val tests = Tests {
    test("hello-world") {
      withDeployedExampleMultiplatformCross("hello-world") {
        runOnAllPlatforms { baseUrl =>
          locally {
            val result = requests.get(baseUrl).text()
            val expectedResult = "Hello world!\n"
            assert(result == expectedResult)
          }
          locally {
            val result = requests.get(s"$baseUrl/version").text()
            val expectedResult = "HTTP/1.1"
            assert(result == expectedResult)
          }
          locally {
            val result = requests.get(s"$baseUrl/target/%2F%2f%5C%5c").text()
            val expectedResult = "/target/%2F%2f%5C%5c"
            assert(result == expectedResult)
          }
          locally {
            val result = requests.get(s"$baseUrl/path/foo%2Fbar%2f%5C%5c").text()
            val expectedResult = """/path/foo/bar/\\"""
            assert(result == expectedResult)
          }
        }
      }
    }
    test("empty-response") {
      withDeployedExample("empty-response") {
        val result = requests.get(baseUrl).text()
        val expectedResult = ""
        assert(result == expectedResult)
      }
    }
    test("async") {
      withDeployedExampleCross("async") {
        val result = requests.get(baseUrl, readTimeout = 10000).text()
        val expectedResult = "Hello world async!\n"
        assert(result == expectedResult)
      }
    }
    test("multiple-handlers") {
      withDeployedExample("multiple-handlers") {
        val getResult = requests.get(baseUrl).text()
        val expectedGetResult = "Hello world multiple handlers!\n"
        assert(getResult == expectedGetResult)

        val postResultResponse = requests.post(baseUrl, check = false)
        val postResult = postResultResponse.text()
        val expectedPostResult = "Not found\n"
        assert(postResult == expectedPostResult)
        assert(postResultResponse.statusCode == 404)
      }
    }
    test("async-multiple-handlers") {
      withDeployedExampleCross("async-multiple-handlers") {
        val getResult = requests.get(baseUrl).text()
        val expectedGetResult = "Hello world async multiple handlers!\n"
        assert(getResult == expectedGetResult)

        val postResultResponse = requests.post(baseUrl, check = false)
        val postResult = postResultResponse.text()
        val expectedPostResult = "Not found\n"
        assert(postResult == expectedPostResult)
        assert(postResultResponse.statusCode == 404)
      }
    }
    test("autowire") {
      withDeployedExample("autowire") {
        val name = "A-Name"
        val asyncResult =
          requests.post(s"$baseUrl/snunit/tests/MyApi/helloAsync", data = s"""{"name": "$name"}""").text()
        assert(asyncResult.contains("Hello "))
        assert(asyncResult.contains(name))
      }
    }
    test("autowire-int") {
      withDeployedExample("autowire-int") {
        val asyncResult = requests.post(s"$baseUrl/snunit/tests/MyApi/addOne", data = s"""{"i": 1}""").text()
        assert(asyncResult == "2")
      }
    }
    test("routes") {
      withDeployedExample("routes") {
        val i = 10
        val getResult = requests.get(s"$baseUrl/test/$i").text()
        val expectedGetResult = s"Got $i"
        assert(getResult == expectedGetResult)
      }
    }
    test("undertow-helloworld") {
      withDeployedExampleMultiplatformCross("undertow-helloworld") {
        runOnAllPlatforms { baseUrl =>
          val result = requests.get(baseUrl).text()
          val expectedResult = "Hello World"
          assert(result == expectedResult)
        }
      }
    }
    test("cask-helloworld") {
      withDeployedExampleMultiplatformCross("cask-helloworld") {
        runOnAllPlatforms { baseUrl =>
          locally {
            val result = requests.get(baseUrl).text()
            val expectedResult = "Hello World!"
            assert(result == expectedResult)
          }

          locally {
            val result = requests.get(s"$baseUrl/hello?name=Lorenzo").text()
            val expectedResult = "Hello Lorenzo!"
            assert(result == expectedResult)
          }

          locally {
            val result = requests.post(s"$baseUrl/do-thing", data = "hello").text()
            val expectedResult = "olleh"
            assert(result == expectedResult)
          }
        }
      }
    }
    def tapirHelloWorldTest(baseUrl: String) = {
      locally {
        val result = requests.get(s"$baseUrl/hello?name=Lorenzo").text()
        val expectedResult = "Hello Lorenzo!"
        assert(result == expectedResult)
      }
      locally {
        val resultResponse = requests.get(s"$baseUrl/inexistent", check = false)
        assert(resultResponse.statusCode == 404)
      }
    }
    test("tapir-helloworld") {
      withDeployedExampleMultiplatformCross("tapir-helloworld") {
        runOnAllPlatforms { baseUrl =>
          tapirHelloWorldTest(baseUrl)
        }
      }
    }
    test("tapir-helloworld-future") {
      withDeployedExampleCross("tapir-helloworld-future") {
        tapirHelloWorldTest(baseUrl)
      }
    }
    test("http4s") {
      withDeployedExampleCross("http4s-helloworld") {
        val result = requests.get(baseUrl).text()
        val expectedResult = "Hello Http4s!"
        assert(result == expectedResult)
      }
    }
  }
}
