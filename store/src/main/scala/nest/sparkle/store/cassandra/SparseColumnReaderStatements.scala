package nest.sparkle.store.cassandra

/** CQL s for SparseColumnReader */
object SparseColumnReaderStatements extends PerTablePrepared {
  case class ReadAll(override val tableName: String) extends TableOperation
  case class ReadRange(override val tableName: String) extends TableOperation
  case class ReadFromStart(override val tableName: String) extends TableOperation
  case class ReadFromStartWithLimit(override val tableName: String) extends TableOperation
  case class ReadLastKey(override val tableName: String) extends TableOperation
  case class ReadFirstKey(override val tableName: String) extends TableOperation
  case class CountAll(override val tableName: String) extends TableOperation
  case class CountRange(override val tableName: String) extends TableOperation
  case class CountFromStart(override val tableName: String) extends TableOperation

  val toPrepare = Seq(
    ReadAll -> readAll _,
    ReadRange -> readRange _,
    ReadFromStart -> readFromStart _,
    ReadFromStartWithLimit -> readFromStartWithLimit _,
    ReadFirstKey -> readFirstKey _,
    ReadLastKey -> readLastKey _,
    CountAll -> countAll _,
    CountRange -> countRange _,
    CountFromStart -> countFromStart _
  )

  private def readAll(tableName: String): String = s"""
      SELECT key, value FROM $tableName
      WHERE columnPath = ? AND bucketStart = ?
      """

  private def readRange(tableName: String): String = s"""
      SELECT key, value FROM $tableName
      WHERE columnPath = ? AND bucketStart = ? AND key >= ? AND key < ?
      """

  private def readFromStart(tableName: String): String = s"""
      SELECT key, value FROM $tableName
      WHERE columnPath = ? AND bucketStart = ? AND key >= ?
      """

  private def readLastKey(tableName: String):String = s"""
      SELECT key FROM $tableName
      WHERE columnPath = ? AND bucketStart = ? ORDER BY key DESC LIMIT 1
      """

  private def readFirstKey(tableName: String):String = s"""
      SELECT key FROM $tableName
      WHERE columnPath = ? AND bucketStart = ? ORDER BY key ASC LIMIT 1
      """

  private def readFromStartWithLimit(tableName: String):String = s"""
      SELECT key, value FROM $tableName
      WHERE columnPath = ? AND bucketStart = ? ORDER BY key ASC LIMIT ?
      """

  private def countAll(tableName: String):String = s"""
      SELECT COUNT(*) FROM $tableName
      WHERE columnPath = ? AND bucketStart = ?
      """

  private def countRange(tableName: String):String = s"""
      SELECT COUNT(*) FROM $tableName
      WHERE columnPath = ? AND bucketStart = ? AND key >= ? AND key < ?
      """

  private def countFromStart(tableName: String):String = s"""
      SELECT COUNT(*) FROM $tableName
      WHERE columnPath = ? AND bucketStart = ? AND key >= ?
      """

}
