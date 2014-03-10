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

import java.nio.file.Files
import org.scalatest.Matchers

class TestDirectoryRegistry extends DirectoryRegistryFixture with Matchers {
  test("watch for changes in directory") {
    val a = Files.createFile(dir.resolve("a.csv"))
    try {
      val files = awaitFileCount(1)
      files(0) should be ("a.csv")
      
      val b = Files.createFile(dir.resolve("b.csv"))
      val files2 = awaitFileCount(2)
      files2(1) should be ("b.csv")
      
      Files.deleteIfExists(b) should be (true)
      val files3 = awaitFileCount(1)
      files3(0) should be ("a.csv")
      
    } finally {
      Files.deleteIfExists(a)
    }
  }

}
