package nest.sparkle.util

import org.scalatest.FunSuite
import org.scalatest.Matchers
import nest.sparkle.util.StableGroupBy._
import org.scalatest.prop.PropertyChecks

class TestStableGroupBy extends FunSuite with Matchers with PropertyChecks {
  def testGroupBy(list: Traversable[Int]): Traversable[(Int, Seq[Int])] = {
    val groups = list.groupBy(x => x)
    val stableGroups = list.stableGroupBy { x => x }
    stableGroups.toMap shouldBe groups
    val keys = stableGroups.map{ case (key, value) => key }.toSeq
    keys shouldBe list.toSeq.distinct
    stableGroups
  }

  test("stable groupBy is correct and stable for a simple list") {
    val result = testGroupBy(List(1, 2, 3, 3, 3, 4))
    val expected = Seq((1, Seq(1)), (2, Seq(2)), (3, (Seq(3, 3, 3))), (4, Seq(4)))
    result shouldBe expected
  }

  test("stableGroupBy works on arbitrary lists of integers") {
    forAll { list: List[Int] =>
      testGroupBy(list)
      ()
    }
  }

}