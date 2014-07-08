/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.legacy

import org.scalatest.Matchers
import java.nio.file.Files

class TestDeepDirectoryRegistry extends DirectoryRegistryFixture with Matchers {
  test("watch for changes in deep directory") {
    val sub = Files.createDirectory(dir.resolve("sub"))
    val a = Files.createFile(sub.resolve("a.csv"))
    var sub2 = dir
    var b = dir
    try {
      val files = awaitFileCount(1)
      files(0) should be ("sub/a.csv")

      sub2 = Files.createDirectory(sub.resolve("sub2"))
      b = Files.createFile(sub2.resolve("b.csv"))
      val files2 = awaitFileCount(2)
      files2(1) should be ("sub/sub2/b.csv")

    } finally {
      List(a,b,sub2,sub) foreach Files.deleteIfExists
    }
  }

}
