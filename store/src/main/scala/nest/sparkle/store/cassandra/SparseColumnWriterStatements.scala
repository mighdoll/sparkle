package nest.sparkle.store.cassandra

/** CQL statements for SparseColumnWriter */
object SparseColumnWriterStatements extends PerTablePrepared {
  case class InsertOne(override val tableName: String) extends TableOperation
  case class DeleteAll(override val tableName: String) extends TableOperation

  val toPrepare = Seq(
    InsertOne -> insertOneStatement _,
    DeleteAll -> deleteAllStatement _
  )

  private def deleteAllStatement(tableName: String): String = s"""
      DELETE FROM $tableName
      WHERE columnPath = ? AND bucketStart = ?
      """

  private def insertOneStatement(tableName: String): String = s"""
      INSERT INTO $tableName
      (columnPath, bucketStart, key, value)
      VALUES (?, ?, ?, ?)
      """
}
