package snunit.plugin

import utest._

object SNUnitPluginSharedTests extends TestSuite {
  val snunitPluginShared = new SNUnitPluginShared(Logger.stderr)
  import snunitPluginShared._
  val tests = Tests {
    test("isUnitInstalled should return true if unitd is available") {
      // throws if not available
      os.proc("which", "unitd").call()
      assert(isUnitInstalled())
    }
  }
}
