/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.store.cassandra

import scala.language._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.collection.JavaConverters._

import com.datastax.driver.core.{ Cluster, Session }
import com.datastax.driver.core.PreparedStatement

import rx.lang.scala.Observable

import com.typesafe.config.Config

import spray.util._

import nest.sparkle.util.Log
import nest.sparkle.util.RandomUtil
import nest.sparkle.util.ObservableFuture._
import nest.sparkle.util.GuavaConverters._
import nest.sparkle.store.{ Store, DataSet, Column, WriteableStore, DataSetNotFound }
import nest.sparkle.store.cassandra.ObservableResultSet._

case class AsciiString(val string: String) extends AnyVal
case class NanoTime(val nanos: Long) extends AnyVal
case class MilliTime(val millis: Long) extends AnyVal

object CassandraStore extends Log {
  /** return a Storage DAO for cassandra.  */
  def apply(config: Config) = new ConfiguredCassandra(config)

  /** Convert a proposed name into a name that's safe to use as a cassandra table name (aka column family).
    * illegal characters are removed.  too long names are partially replaced with a random string
    * e.g. my.server.name.is.too.long.what.shall.i.store.latency.p99 will be replaced with
    * myvservervnamevi16RandomDigits16.  The caller is expected to maintain her own mapping from
    * proposed names to sanitized names.
    */
  protected[cassandra] def sanitizeTableName(proposedName: String): String = {
    val sanitized: Seq[Char] =
      proposedName.map {
        case c if c.isLetterOrDigit => c
        case _                      => 'v'
      }
    if (sanitized.length < 32) {
      sanitized.mkString
    } else {
      sanitized.take(16).mkString + RandomUtil.randomAlphaNum(16)
    }
  }

  /** Drop the keyspace.
    * This is mostly useful for testing.
    *
    * @param contactHosts Cassandra host to create session for.
    * @param keySpace keyspace to drop.
    */
  def dropKeySpace(contactHosts: Seq[String], keySpace: String) {
    val clusterSession = getClusterSession(contactHosts)
    try {
      log.info(s"dropping keySpace: $keySpace")
      clusterSession.session.execute(s"DROP KEYSPACE IF EXISTS $keySpace")
    } finally {
      clusterSession.close()
    }
  }

  /** Drop the keyspace.
    * This is mostly useful for testing.
    *
    * @param contactHost Cassandra host to create session for.
    * @param keySpace keyspace to drop.
    */
  def dropKeySpace(contactHost: String, keySpace: String) {
    dropKeySpace(Seq(contactHost), keySpace)
  }

  /** Create a connection to the cassandra cluster
    *
    * @param contactHosts Hosts to connect to
    * @return Cassandra session
    */
  protected def getClusterSession(contactHosts: Seq[String]): ClusterSession = {
    val builder = Cluster.builder()
    contactHosts.foreach{ builder.addContactPoint(_) }
    val cluster = builder.build()
    val session = cluster.connect()
    ClusterSession(cluster, session)
  }

  /** Create a connection to the cassandra cluster using the single host.
    *
    * @param contactHost Host to connect to
    * @return Cassandra session
    */
  protected def getClusterSession(contactHost: String): ClusterSession = {
    getClusterSession(Seq(contactHost))
  }

}

/** a cassandra store data access layer configured by a config file */
class ConfiguredCassandra(config: Config) extends CassandraStore {
  val storeConfig = config.getConfig("sparkle-store-cassandra")
  override val contactHosts: Seq[String] = storeConfig.getStringList("contact-hosts").asScala.toSeq
  override val storeKeySpace = storeConfig.getString("key-space")
}

/** a Storage data access object for Cassandra. Supports the Store and WriteableStore apis for 
 *  reading/writing columns and column metadata.  */
trait CassandraStore extends Store with WriteableStore with Log {
  lazy val columnCatalog = ColumnCatalog(session)
  lazy val dataSetCatalog = DataSetCatalog(session)

  def contactHosts: Seq[String]
  val storeKeySpace: String
  implicit def execution: ExecutionContext = ExecutionContext.global // TODO use a provided execution context

  /** current cassandra session.  (Currently we use one session for this CassandraStore) */
  implicit lazy val session: Session = {
    useKeySpace(clusterSession.session)
    clusterSession.session
  }

  /** create a connection to the cassandra cluster */
  lazy val clusterSession: ClusterSession = {
    try {
      log.info(s"""starting session using contact hosts on ${contactHosts.mkString(",")}""")
      CassandraStore.getClusterSession(contactHosts)
    } catch {
      case e: Exception => log.error("session creation failed", e); throw e
    }
  }

  /** Close the connection to Cassandra.
    *
    * Blocks the calling thread until the session is closed
    */
  def close() { clusterSession.close() }

  /** Return the dataset for the provided dataSet path (fooSet/barSet/mySet).
    *
    * A check is made that the dataSet exists. If not the Future is failed with
    * a DataSetNotFound returned.
    */
  def dataSet(dataSetPath: String): Future[DataSet] = {
    // Check there are any entries with this path as the parent.
    val future = dataSetCatalog.childrenOfParentPath(dataSetPath).toFutureSeq
    future.flatMap { children =>
      children.size match {
        case n if n > 0 => Future.successful(CassandraDataSet(this, dataSetPath))
        case _          => Future.failed(DataSetNotFound(s"$dataSetPath does not exist"))
      }
    }
  }

  /** return a column from a fooSet/barSet/columName path */
  def column[T, U](columnPath: String): Future[Column[T, U]] = {
    val (dataSetName, columnName) = Store.setAndColumn(columnPath)
    val futureColumn = SparseColumnReader.instance[T, U](dataSetName, columnName, columnCatalog)
    futureColumn
  }

  /** return a column from a fooSet/barSet/columName path */
  def writeableColumn[T: CanSerialize, U: CanSerialize](columnPath: String): Future[WriteableColumn[T, U]] = {
    val (dataSetName, columnName) = Store.setAndColumn(columnPath)
    SparseColumnWriter.instance[T, U](dataSetName, columnName, columnCatalog, dataSetCatalog)
  }

  /** Create the tables using the session passed.
    * The session's keyspace itself must already exist.
    * Any existing tables are deleted.
    *
    * This call is synchronous.
    */
  def format() {
    format(session)
  }

  /** Make sure the keyspace exists, creating it if necessary, and set the cassandra driver
    * session to use the default keyspace.
    *
    * @param session The session to use. This shadows the instance variable
    *            because the instance variable may not be initialized yet.
    */
  private def useKeySpace(session: Session) {
    val keySpacesRows = session.executeAsync(s"""
        SELECT keyspace_name FROM system.schema_keyspaces""").observerableRows

    val keySpaces = keySpacesRows.toFutureSeq.await
    log.trace(s"useKeySpace checking keySpaces: $keySpaces")
    val keySpaceFound = keySpaces.map(_.getString(0).toLowerCase).contains(storeKeySpace)

    if (keySpaceFound) {
      session.execute(s"USE $storeKeySpace")
    } else {
      createKeySpace(session, storeKeySpace)
    }
  }

  /** create a keyspace (db) in cassandra */
  private def createKeySpace(session: Session, keySpace: String) {
    session.execute(s"""
        CREATE KEYSPACE $keySpace
        with replication = {'class': 'SimpleStrategy', 'replication_factor': 1}"""
    )
    session.execute(s"USE $keySpace")
    format(session)
  }

  /** Create the tables using the session passed.
    * The session's keyspace itself must already exist.
    * Any existing tables are deleted.
    */
  protected def format(session: Session) {
    dropTables(session)

    SparseColumnWriter.createColumnTables(session).await
    ColumnCatalog.create(session)
    DataSetCatalog.create(session)
  }

  /** Drop all tables in the keyspace.
    */
  private def dropTables(session: Session) = {
    val query = s"""SELECT columnfamily_name FROM system.schema_columnfamilies
      WHERE keyspace_name = '$storeKeySpace'"""
    val rows = session.executeAsync(query).observerableRows
    val drops = rows.map { row =>
      val tableName = row.getString(0)
      dropTable(session, tableName)
    }
    drops.toBlockingObservable foreach { drop => drop.await }
  }

  /** Delete a table (and all of the data in the table) from the session's current keyspace
    */
  private def dropTable(session: Session, tableName: String) // format: OFF
      (implicit execution: ExecutionContext): Future[Unit] = { // format: ON
    val dropTable = s"DROP TABLE IF EXISTS $tableName"
    session.executeAsync(dropTable).toFuture.map { _ => () }
  }

}
