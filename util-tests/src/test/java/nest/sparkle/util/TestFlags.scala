package nest.sparkle.util

import org.scalatest.{FunSuite, Matchers}

class TestFlags extends FunSuite with Matchers {

  test("fields descriptor") {
    val flags = DummyFlags(0)
    val fields = flags.fields
    fields.positionOfField("aaa") shouldBe Some(0)
    fields.positionOfField("bbb") shouldBe Some(1)
    fields.positionOfField("ccc") shouldBe Some(2)
    fields.positionOfField("unknown") shouldBe None
  }

  test("update") {
    var flags: Flags = DummyFlags(0)
    flags = updatedFlags(flags, 0, true)  // 000011
    flags shouldBe DummyFlags(3)
    flags = updatedFlags(flags, 1, false) // 000111
    flags shouldBe DummyFlags(7)
    flags = updatedFlags(flags, 2, false) // 010111
    flags shouldBe DummyFlags(23)
  }

  def updatedFlags(flags: Flags, position: Int, flag: Boolean): Flags = {
    flags.fieldValue(position) shouldBe None
    val updatedFlags = flags.updatedFlags(position, flag)
    updatedFlags.fieldValue(position) shouldBe Some(flag)
    updatedFlags
  }

}

case class DummyFlags(value: Long) extends AnyVal with Flags {
  override def fields = DummyFieldsDescriptor
  override def updatedFlags(position: Int, value: Boolean): Flags = {
    new DummyFlags(updatedValue(position, value))
  }
}

object DummyFieldsDescriptor extends FieldsDescriptor {
  val fieldPositions: Map[String, Int] = Map(
    "aaa" -> 0,
    "bbb" -> 1,
    "ccc" -> 2
  )
}