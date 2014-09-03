package nest.sparkle.store.cassandra

import com.datastax.driver.core.{ PreparedStatement, Session }
import nest.sparkle.util.Log
import scala.util.control.NonFatal

trait TableOperation { def tableName: String }

case class SessionInitializationError() extends RuntimeException

/** utility class for preparing CQL statements */
trait PrepareTableOperations extends Log {
  /** client should override to specify the operations and statement functions  */
  def prepareStatements: List[(String => TableOperation, String => String)]

  private var initialSession: Option[Session] = None

  private lazy val prepared: Map[TableOperation, PreparedStatement] = {
    val session = initialSession.getOrElse { throw SessionInitializationError() }
    val list: List[(TableOperation, PreparedStatement)] = {
      val tableNames: Seq[String] =
        for {
          serialInfo <- ColumnTypes.supportedColumnTypes
          tableName = serialInfo.tableName
        } yield {
          tableName
        }
      val uniqueTables = tableNames.toSet

      val opStatements: List[(TableOperation, PreparedStatement)] =
        for {
          (tableOperation, statementFn) <- prepareStatements
          tableName <- uniqueTables
        } yield {
          val statementText = statementFn(tableName)
          val statement =
            try {
              session.prepare(statementText)
            } catch {
              case err:RuntimeException => 
                log.error(s"prepare failed: $err")
                throw err
            }

          tableOperation(tableName) -> statement
        }

      opStatements
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
