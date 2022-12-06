import utest._

import TestUtils._

object BaseTests extends TestSuite {
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
      withDeployedExample("async") {
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
      withDeployedExample("async-multiple-handlers") {
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
  }
}
