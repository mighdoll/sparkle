package nest.sparkle.store.cassandra


/** a trait for creating multiple prepared statements, one for each type of table */
trait PerTablePrepared {
  /** specifiy a sequence of tuples containing the requisite
    * functions: both take a table name as a parameter. one returns a TableOperation (an index key) to the
    * eventual prepared statement table. The other function returns a CQL query string.
    */
  def toPrepare:Seq[(String => TableOperation, String => String)]

  def statementMakers:Seq[StatementMaker] =
    toPrepare.map { case (makeTableOperation, makeStatement) => StatementMaker(makeTableOperation, makeStatement) }
}


/** Functions to create a CQL statement and a TableOperation key based on a string table name.
 *  This is used to construct the prepared statement table in PreparedSession
 */
case class StatementMaker(
  val makeTableOperation: String => TableOperation,
  val makeStatement: String => String)

object StatementMaker {
  /** specify a sequence of StatementMakers by specifying a sequence of tuples containing the requisite
    * functions: both take a table name as a parameter. one returns a TableOperation (an index key) to the
    * eventual prepared statement table. The other function returns a CQL query string.
    */
  def fromFunctions(functions: Seq[(String => TableOperation, String => String)]): Seq[StatementMaker] = {
    functions.map { case (makeTableOperation, makeStatement) => StatementMaker(makeTableOperation, makeStatement) }
  }
}
