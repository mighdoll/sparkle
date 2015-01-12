/* Copyright 2014  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.loader

import org.scalatest.FunSuite
import org.scalatest.Matchers
import java.nio.file.Paths
import spray.util._
import akka.util.Timeout
import scala.concurrent.duration._
import org.scalatest.Finders
import scala.concurrent.ExecutionContext
import nest.sparkle.util.Managed.implicits._
import nest.sparkle.util.Resources
import nest.sparkle.test.SparkleTestConfig

class TestTextTableParser extends FunSuite with Matchers with SparkleTestConfig {
  import ExecutionContext.Implicits.global
  
  def loadRowInfo(resourcePath: String)(fn: CloseableRowInfo => Unit) {
    val filePath = Resources.filePathString(resourcePath)
    val closeableRowInfo = TabularFile.load(Paths.get(filePath)).await

    managed(closeableRowInfo) foreach fn
  }

  test("load sample.csv file") {
    loadRowInfo("sample.csv") { rowInfo =>
      rowInfo.valueColumns.length shouldBe 25
      rowInfo.rows.toSeq.size shouldBe 78
    }
  }

  test("load csv file with numeric timestamps") {
    loadRowInfo("epochs.csv") { rowInfo =>
      rowInfo.valueColumns.length shouldBe 3
      rowInfo.rows.toSeq.size shouldBe 2751
    }
  }

  test("load csv file with numeric timestamps and no header") {
    loadRowInfo("just-time.csv") { rowInfo =>
      rowInfo.rows.toSeq.size shouldBe 4
      rowInfo.valueColumns.size shouldBe 0
    }
  }

  test("test time parser") {
    TextTableParser.parseTime("2013-02-15T01:32:48.955") should be (Some(1360891968955L))
    TextTableParser.parseTime("1357710557000") should be (Some(1357710557000L))
    TextTableParser.parseTime("1373681685.0") should be (Some(1373681685000L))
  }

}
