package nest.sparkle.store.cassandra

/** CQL statements for SparseColumnReader */
object SparseColumnReaderStatements extends PerTablePrepared {
  case class ReadAll(override val tableName: String) extends TableOperation
  case class ReadRange(override val tableName: String) extends TableOperation
  case class ReadFromStart(override val tableName: String) extends TableOperation
  case class ReadFromStartWithLimit(override val tableName: String) extends TableOperation
  case class ReadLastKey(override val tableName: String) extends TableOperation
  case class ReadFirstKey(override val tableName: String) extends TableOperation
  case class CountAllItems(override val tableName: String) extends TableOperation
  case class CountRangeItems(override val tableName: String) extends TableOperation

  val toPrepare = Seq(
    ReadAll -> readAllStatement _,
    ReadRange -> readRangeStatement _,
    ReadFromStart -> readFromStartStatement _,
    ReadFromStartWithLimit -> readFromStartWithLimit _,
    ReadFirstKey -> readFirstKeyStatement _,
    ReadLastKey -> readLastKeyStatement _,
    CountAllItems -> countAllItemsStatement _,
    CountRangeItems -> countRangeItemsStatement _
  )

  private def readAllStatement(tableName: String): String = s"""
      SELECT argument, value FROM $tableName
      WHERE dataSet = ? AND column = ? AND rowIndex = ?
      """

  private def readRangeStatement(tableName: String): String = s"""
      SELECT argument, value FROM $tableName
      WHERE dataSet = ? AND column = ? AND rowIndex = ? AND argument >= ? AND argument < ?
      """

  private def readFromStartStatement(tableName: String): String = s"""
      SELECT argument, value FROM $tableName
      WHERE dataSet = ? AND column = ? AND rowIndex = ? AND argument >= ? 
      """

  private def readLastKeyStatement(tableName: String):String = s"""
      SELECT argument FROM $tableName
      WHERE dataSet = ? AND column = ? AND rowIndex = ? ORDER BY argument DESC LIMIT 1
      """

  private def readFirstKeyStatement(tableName: String):String = s"""
      SELECT argument FROM $tableName
      WHERE dataSet = ? AND column = ? AND rowIndex = ? ORDER BY argument ASC LIMIT 1
      """

  private def readFromStartWithLimit(tableName: String):String = s"""
      SELECT argument, value FROM $tableName
      WHERE dataSet = ? AND column = ? AND rowIndex = ? ORDER BY argument ASC LIMIT ?
      """

  private def countAllItemsStatement(tableName: String):String = s"""
      SELECT COUNT(*) FROM $tableName
      WHERE dataSet = ? AND column = ? AND rowIndex = ?
      """

  private def countRangeItemsStatement(tableName: String):String = s"""
      SELECT COUNT(*) FROM $tableName
      WHERE dataSet = ? AND column = ? AND rowIndex = ? AND argument >= ? AND argument < ?
      """

}