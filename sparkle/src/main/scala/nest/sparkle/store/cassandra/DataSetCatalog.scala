package nest.sparkle.store.cassandra

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import rx.lang.scala.Observable
import com.datastax.driver.core.Session
import com.datastax.driver.core.PreparedStatement
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.Row
import nest.sparkle.util.GuavaConverters._
import nest.sparkle.util.OptionConversion._
import nest.sparkle.store.cassandra.ObservableResultSet._
import nest.sparkle.util.Log

/**
 * An entry in the DataSetCatalog table is a 'sub-folder' or column for a path.
 *
 * @param parentPath One or more part dataset name of the parent, e.g. a/b/c
 * @param childPath Full path of the child contained in the parent, e.g. a/b/c/column1
 * @param isColumn True if the child path is a columnPath and not an intermediate dataSet component (folder).
 */
case class DataSetCatalogEntry(parentPath: String, childPath: String, isColumn: Boolean)

case class DataSetCatalogStatements(addChildPath: PreparedStatement,
                                    childrenOfParentPath: PreparedStatement)

/**
 * The DataSetCatalog stores the immediate children of a dataSet path.
 * Children may be a component of a DataSet 'path' or a columnPath.
 *
 * This is needed by the Cassandra store so that efficient queries
 * for child elements can be made, e.g. all of the columns for a DataSet.
 *
 * catalog:
 *   a/b1/col1
 *   a/b1/col2
 *   a/b2/col1
 *   a/b2/col2
 *
 * datsets: storage view:
 *   a    -> a/b1:false, a/b2:false
 *   a/b1 -> a/b1/col1:true, a/b1/col2:true
 *   a/b2 -> a/b2/col1:true, a/b2/col2:true
 */
case class DataSetCatalog(session:Session)
  extends PreparedStatements[DataSetCatalogStatements] with Log
{
  val tableName = DataSetCatalog.tableName

  val addChildPathStatement = s"""
      INSERT INTO $tableName
      (parentPath, childPath, isColumn)
      VALUES (?, ?, ?);
    """

  val childrenOfParentPathStatement = s"""
      SELECT parentPath, childPath, isColumn FROM $tableName
      WHERE parentPath = ? ORDER BY childPath ASC;
    """

  lazy val catalogStatements: DataSetCatalogStatements = {
    preparedStatements(makeStatements)
  }

  /**
   * Return prepared statements
   */
  def makeStatements(): DataSetCatalogStatements = {
    DataSetCatalogStatements(
      session.prepare(addChildPathStatement),
      session.prepare(childrenOfParentPathStatement)
    )
  }

  /**
   * Add a columnPath to a parent DataSet in the DataSetCatalog.
   * Recursive entries for each of the components of the parent are created if needed.
   *
   * @param columnPath Full path to child
   * @param executionContext yeah
   * @return Future to wait on for success or failure.
   */
  def addColumnPath(columnPath: String)
      (implicit executionContext: ExecutionContext): Future[Unit] = {

    // @dkorz what do you think we ought do with leading slashes?  (null key on insert if allowed)
    val pairs = columnPath.stripPrefix("/").split("/").scanLeft("") {
      case ("", x)   => x
      case (last, x) => last + "/" + x
    }.tail.sliding(2).toSeq

    val futures = pairs.dropRight(1).map { pair =>
      addChildPath(pair(0), pair(1), false)
    }.toList

    val last = addChildPath(pairs.last(0), pairs.last(1), true)

    val result = Future.sequence(last :: futures).map {_ => ()}
    result.onFailure{ case error => log.error("add ColumnPath failed", error)}
    result
  }

  /**
   * Add an entry to a path's parent with this path as a child.
   * The path may either be a dataset component or a columnPath.
   *
   * @param parentPath Full path to parent
   * @param childPath Full path to child
   * @param isColumn True if the child is the path to a column. If false the child is a part of a dataSet.
   * @param executionContext
   * @return Future to wait on for success or failure.
   */
  private def addChildPath(parentPath: String, childPath: String, isColumn: Boolean)
      (implicit executionContext: ExecutionContext): Future[Unit] = {
    val statement = catalogStatements.addChildPath.bind(parentPath,childPath,isColumn: java.lang.Boolean)
    session.executeAsync(statement).toFuture.map{ _ => }
  }

  /**
   * Return all of children of a dataset path.
   * The children may be intermediate dataset levels or columnPaths.
   *
   * Note some DataSets may have a large number of children specifically
   * multilevel datasets, e.g. sapphire with all devices under it.
   *
   * @param parentPath Path to get children for
   * @param executionContext
   * @return Observable of DataSetCatalogEntry item.
   */
  def childrenOfParentPath(parentPath: String) // format: OFF
      (implicit executionContext: ExecutionContext):Observable[DataSetCatalogEntry] = { // format: ON
    val statement = catalogStatements.childrenOfParentPath.bind(parentPath)

    // result will be a zero or more rows for each child.
    val rows = session.executeAsync(statement).observerableRows
    rows map rowDecoder
  }

  /**
   * Convert a row from the datasetCatalog table into a DataSetCatalogEntry.
   *
   * @param row Cassandra row from the dataset catalog table.
   * @return DataSetCatalogEntry from the row
   */
  private def rowDecoder(row: Row): DataSetCatalogEntry = {
    val parentPath = row.getString(0)
    val childPath = row.getString(1)
    val isColumn = row.getBool(2)
    DataSetCatalogEntry(parentPath, childPath, isColumn)
  }
}

object DataSetCatalog {

  val tableName = "dataset_catalog"

  /**
   * Create the DataSetCatalog table.
   *
   * @param session Session to use.
   */
  def create(session:Session): Unit = {
    session.execute(s"""
      CREATE TABLE IF NOT EXISTS $tableName (
        parentPath text,
        childPath text,      // children
        isColumn Boolean,
        PRIMARY KEY(parentPath, childPath)
      );"""
    )

  }

}
