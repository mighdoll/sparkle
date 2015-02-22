package nest.sparkle.store.cassandra

/** CQL statements for SparseColumnReader */
object SparseColumnReaderStatements extends PerTablePrepared {
  case class ReadAll(override val tableName: String) extends TableOperation
  case class ReadRange(override val tableName: String) extends TableOperation
  case class ReadFromStart(override val tableName: String) extends TableOperation
  case class ReadFromStartWithLimit(override val tableName: String) extends TableOperation
  case class ReadFromEndWithLimit(override val tableName: String) extends TableOperation
  case class CountItems(override val tableName: String) extends TableOperation

  val toPrepare = Seq(
    ReadAll -> readAllStatement _,
    ReadRange -> readRangeStatement _,
    ReadFromStart -> readFromStartStatement _,
    ReadFromStartWithLimit -> readFromStartWithLimit _,
    ReadFromEndWithLimit -> readFromEndWithLimit _,
    CountItems -> countItemsStatement _
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

  private def readFromEndWithLimit(tableName: String):String = s"""
      SELECT argument FROM $tableName
      WHERE dataSet = ? AND column = ? AND rowIndex = ? ORDER BY argument DESC LIMIT ?
      """

  private def readFromStartWithLimit(tableName: String):String = s"""
      SELECT argument FROM $tableName
      WHERE dataSet = ? AND column = ? AND rowIndex = ? ORDER BY argument ASC LIMIT ?
      """

  private def countItemsStatement(tableName: String):String = s"""
      SELECT COUNT(*) FROM $tableName
      WHERE dataSet = ? AND column = ? AND rowIndex = ?
      """
}