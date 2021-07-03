package snunit.plugin

import java.nio.file.{Files, Paths}
import java.nio.file.attribute._

object ClangScripts {
  case class Script(path: String, content: String)
  case class Scripts(clang: Script, clangpp: Script)

  def createClangScripts(dest: String, pwd: String): Scripts = {
    def absolute(basePath: String, others: String*) =
      Paths.get(basePath, others: _*).toAbsolutePath().normalize().toString()
    val pwdAbsolute = absolute(pwd)
    def script(executable: String) =
      s"""#!/bin/bash
         |docker run -v "$pwdAbsolute:$pwdAbsolute" --entrypoint $executable lolgab/snunit-clang:0.0.1 "$$@"
         |""".stripMargin

    Scripts(
      clang = Script(absolute(dest, "clang-scripts", "clang.sh"), script("clang")),
      clangpp = Script(absolute(dest, "clang-scripts", "clang++.sh"), script("clang++"))
    )
  }

  def writeScript(script: Script): Unit = {
    val p = Paths.get(script.path)
    Files.createDirectories(p.getParent())
    Files.write(p, script.content.getBytes())
    p.toFile().setExecutable(true)
  }

  def createAndWriteClangScripts(dest: String, pwd: String): Scripts = {
    val scripts = createClangScripts(dest = dest, pwd = pwd)
    writeScript(scripts.clang)
    writeScript(scripts.clangpp)
    scripts
  }
}
