package nest.sparkle.store.cassandra

import com.datastax.driver.core.{PreparedStatement, Session}

trait TableOperation { def tableName: String }

case class SessionInitializationError() extends RuntimeException

/** utility class for preparing CQL statements */
trait PrepareTableOperations {
  /** client should override to specify the operations and statement functions  */
  def prepareStatements: List[(String => TableOperation, String => String)]

  private var initialSession: Option[Session] = None

  private lazy val prepared: Map[TableOperation, PreparedStatement] = {
    val session = initialSession.getOrElse{ throw SessionInitializationError() }
    val list: List[(TableOperation, PreparedStatement)] =
      for {
        (tableOp, statementFn) <- prepareStatements
        serialInfo <- ColumnTypes.supportedColumnTypes
        tableName = serialInfo.tableName
      } yield {
        val statementText = statementFn(tableName)
        val statement = session.prepare(statementText)
        tableOp(tableName) -> statement
      }

    list.toMap
  }

  /** return prepared statement for a given intended operation */
  def statement(tableOperation: TableOperation)(implicit session: Session): PreparedStatement = {
    initialSession = Some(session)
    prepared(tableOperation)
  }

  /** prepare a CQL query for every type of column we support */
  private def prepareOperations(session: Session, statementFn: String => String, tableOp: String => TableOperation): Map[TableOperation, PreparedStatement] = {
    ColumnTypes.supportedColumnTypes.map { serialInfo =>
      val statementText = statementFn(serialInfo.tableName)
      val statement = session.prepare(statementText)
      tableOp(serialInfo.tableName) -> statement
    }.toMap
  }

}