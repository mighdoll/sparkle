package nest.sparkle.store.cassandra

import com.datastax.driver.core.{ConsistencyLevel, PreparedStatement, Session}
import scala.util.control.Exception._
import nest.sparkle.util.Log

/** Index key for a prepared statement. PreparedStatements are created once for each table, so the key
  * includes a tableName field.
  */
trait TableOperation { def tableName: String }

/** a C* session combined with prepared statements active on that session. Users
  * provide some functions to create CQL statements, and then access them via the statement() method
  */
case class PreparedSession(val session: Session, perTablePrepared:PerTablePrepared,
    val consistencyLevel: ConsistencyLevel) extends Log {

  /** return prepared statement for a given intended operation. Throws if statement is not found. */
  def statement(tableOperation: TableOperation): PreparedStatement = {
    prepared(tableOperation)
  }

  /** map of CQL preparedStatements for this session, indexed by TableOperation */
  private lazy val prepared: Map[TableOperation, PreparedStatement] = {
    val uniqueTables = uniqueTableNames()
    val statementMappings: Seq[(TableOperation, PreparedStatement)] =
      for {
        StatementMaker(makeTableOp, makeStatement) <- perTablePrepared.statementMakers
        tableName <- uniqueTables
        tableOperation = makeTableOp(tableName)
        statementText = makeStatement(tableName)
      } yield {
        val statement = nonFatalCatch.withTry { session.prepare(statementText).setConsistencyLevel(consistencyLevel) }.get
        tableOperation -> statement
      }

    statementMappings.toMap
  }

  /** Return the list of table names currently supported (bigint0double, bigint0boolean, etc.)
    * so that we can precreate a table name for each type of table in the store.
    */
  private def uniqueTableNames(): Set[String] = {
    // table name for each supported table type
    val tableNames: Seq[String] =
      for {
        serialInfo <- ColumnTypes.supportedColumnTypes
        tableName = serialInfo.tableName
      } yield {
        tableName
      }
    tableNames.toSet
  }

}