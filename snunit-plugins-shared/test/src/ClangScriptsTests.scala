package snunit.plugin

import utest._

object ClangScriptsTests extends TestSuite {
  val tests = Tests {
    test("createClangScripts") {
      val pwd = os.pwd.toString
      val result = ClangScripts.createClangScripts(".", ".")

      val expected = ClangScripts.Scripts(
        clang = ClangScripts.Script(
          path = s"$pwd/clang-scripts/clang.sh",
          content = s"""#!/bin/bash
                      |docker run -v "$pwd:$pwd" --entrypoint clang lolgab/snunit-clang:0.0.1 "$$@"
                      |""".stripMargin
        ),
        clangpp = ClangScripts.Script(
          path = s"$pwd/clang-scripts/clang++.sh",
          content = s"""#!/bin/bash
              |docker run -v "$pwd:$pwd" --entrypoint clang++ lolgab/snunit-clang:0.0.1 "$$@"
              |""".stripMargin
        )
      )
      assert(result == expected)
    }
    test("run scripts") {
      val dir = os.temp.dir()
      try {
        val scripts = ClangScripts.createAndWriteClangScripts(dir.toString(), dir.toString())
        os.proc(scripts.clang.path, "--version").call()
        os.proc(scripts.clangpp.path, "--version").call()
      } finally os.remove.all(dir)
    }
  }
}
