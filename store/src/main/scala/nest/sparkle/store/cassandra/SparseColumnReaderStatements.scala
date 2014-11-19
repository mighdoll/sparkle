package nest.sparkle.store.cassandra

/** CQL statements for SparseColumnReader */
object SparseColumnReaderStatements extends PerTablePrepared {
  case class ReadAll(override val tableName: String) extends TableOperation
  case class ReadRange(override val tableName: String) extends TableOperation
  case class ReadFromStart(override val tableName: String) extends TableOperation

  val toPrepare = Seq(
    ReadAll -> readAllStatement _,
    ReadRange -> readRangeStatement _,
    ReadFromStart -> readFromStartStatement _
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

}