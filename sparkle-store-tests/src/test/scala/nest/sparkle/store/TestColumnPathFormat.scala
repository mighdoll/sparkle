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

package nest.sparkle.store

import scala.util.{Success, Failure}
import org.scalatest.{FunSuite, Matchers}

class TestColumnPathFormat extends FunSuite with Matchers {

  test("BasicColumnPathFormat - column category") {
    val columnPathFormat = new BasicColumnPathFormat

    columnPathFormat.columnCategory("epochs/p99") shouldBe
      Success("epochs/p99")

    columnPathFormat.columnCategory("march-27-0800/epochs/p99") shouldBe
      Success("march-27-0800/epochs/p99")

    columnPathFormat.columnCategory("loadtests/march-27-0800/epochs/p99") shouldBe
      Success("loadtests/march-27-0800/epochs/p99")
  }

  test("BasicColumnPathFormat - entity") {
    val columnPathFormat = new BasicColumnPathFormat

    columnPathFormat.entity("epochs/p99") shouldBe
      Success(Entity("epochs", Set("epochs")))

    columnPathFormat.entity("march-27-0800/epochs/p99") shouldBe
      Success(Entity("march-27-0800/epochs", Set("march-27-0800")))

    columnPathFormat.entity("loadtests/march-27-0800/epochs/p99") shouldBe
      Success(Entity("loadtests/march-27-0800/epochs", Set("march-27-0800")))

    columnPathFormat.entity("p99") shouldBe
      Failure(EntityNotDeterminable("p99"))
  }

  test("BasicColumnPathFormat - entity column path") {
    val columnPathFormat = new BasicColumnPathFormat

    columnPathFormat.entityColumnPath("epochs/p99", "epochs") shouldBe
      Success(Some("epochs/p99"))

    columnPathFormat.entityColumnPath("epochs/p90", "epochs") shouldBe
      Success(Some("epochs/p90"))

    columnPathFormat.entityColumnPath("other/p90", "epochs") shouldBe
      Success(None)
  }

  test("BasicColumnPathFormat - leaf dataSet column path") {
    val columnPathFormat = new BasicColumnPathFormat

    columnPathFormat.leafDataSetColumnPath("epochs/p99", "epochs") shouldBe
      Success(Some("epochs/p99"))

    columnPathFormat.leafDataSetColumnPath("epochs/p90", "epochs") shouldBe
      Success(Some("epochs/p90"))

    columnPathFormat.leafDataSetColumnPath("other/p90", "epochs") shouldBe
      Success(None)
  }
}
