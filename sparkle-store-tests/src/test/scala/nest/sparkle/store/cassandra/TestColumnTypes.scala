package nest.sparkle.store.cassandra

import org.scalatest.{FunSuite, Matchers}

class TestColumnTypes extends FunSuite with Matchers {
  test("validate table names") {
    ColumnTypes.validateTableName("") shouldBe false
    ColumnTypes.validateTableName("bigint0bigint") shouldBe true
    ColumnTypes.validateTableName("stringisexactly32chanracterslong") shouldBe true
    ColumnTypes.validateTableName("tablenameislongerthan32chanracters") shouldBe false
    ColumnTypes.validateTableName("table-name-with-dash") shouldBe false
  }
}
