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
import scala.util.control.Exception._

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
  /** (for tests) return a Storage DAO for reading and writing to cassandra.  */
  def readerWriter(config: Config, writeNotification: WriteListenNotify) // format: OFF
       : CassandraReaderWriter // format: ON
       = new ConfiguredCassandraReaderWriter(config, writeNotification)

  /** Drop the keyspace.
    * This is mostly useful for testing.
    *
    * @param contactHosts Cassandra host to create session for.
    * @param keySpace keyspace to drop.
    */
  def dropKeySpace(contactHosts: Seq[String], keySpace: String): Unit = {
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
  def dropKeySpace(contactHost: String, keySpace: String): Unit = {
    dropKeySpace(Seq(contactHost), keySpace)
  }

  /** Create a connection to the cassandra cluster
    *
    * @param contactHosts Hosts to connect to
    * @return Cassandra session
    */
  protected[cassandra] def getClusterSession(contactHosts: Seq[String]): ClusterSession = {
    val builder = Cluster.builder()
    contactHosts.foreach { host =>
      val added = nonFatalCatch.withTry { builder.addContactPoint(host) }
      added.failed.map { err => log.error(s"unable to add cassandra contact point: $host", err) }
    }  // TODO add test for me

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
class ConfiguredCassandraReader(override val config: Config, override val writeListener: WriteListener) extends ConfiguredCassandra with CassandraStoreReader

/** a cassandra store data access layer configured by a config file */
class ConfiguredCassandraWriter(override val config: Config, override val writeNotifier: WriteNotifier) extends ConfiguredCassandra with CassandraStoreWriter

class ConfiguredCassandraReaderWriter(override val config: Config, writeNotification: WriteListener with WriteNotifier) // format: OFF
  extends ConfiguredCassandra with CassandraReaderWriter {  // TODO DRY these
  override def writeNotifier = writeNotification
  override def writeListener = writeNotification
}

trait ConfiguredCassandra extends Log {
  def config:Config
  
  private lazy val storeConfig = config.getConfig("sparkle-store-cassandra")
  private lazy val contactHosts: Seq[String] = storeConfig.getStringList("contact-hosts").asScala.toSeq
  private lazy val storeKeySpace = storeConfig.getString("key-space").toLowerCase
  private lazy val replicationFactor = storeConfig.getInt("replication-factor")
  implicit def execution: ExecutionContext = ExecutionContext.global // TODO use a provided execution context

  lazy val columnCatalog = ColumnCatalog(session)
  lazy val dataSetCatalog = DataSetCatalog(session)

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
  def close(): Unit = { clusterSession.close() }


  /** Make sure the keyspace exists, creating it if necessary, and set the cassandra driver
    * session to use the default keyspace.
    *
    * @param session The session to use. This shadows the instance variable
    *            because the instance variable may not be initialized yet.
    */
  private def useKeySpace(session: Session): Unit = {
    val keySpacesRows = session.executeAsync(s"""
        SELECT keyspace_name FROM system.schema_keyspaces""").observerableRows

    val keySpaces = keySpacesRows.toFutureSeq.await
    log.debug(s"useKeySpace checking keySpaces: $keySpaces")
    val keySpaceFound = keySpaces.map(_.getString(0).toLowerCase).contains(storeKeySpace)

    log.info(s"using keySpace: $storeKeySpace")
    if (keySpaceFound) {
      session.execute(s"USE $storeKeySpace")
    } else {
      createKeySpace(session, storeKeySpace)
    }

  }

  /** create a keyspace (db) in cassandra */
  private def createKeySpace(session: Session, keySpace: String): Unit = {
    session.execute(s"""
        CREATE KEYSPACE $keySpace
        with replication = {'class': 'SimpleStrategy', 'replication_factor': $replicationFactor}"""
    )
    session.execute(s"USE $keySpace")
    format(session)
  }
  

  /** Create the tables using the session passed.
    * The session's keyspace itself must already exist.
    * Any existing tables are deleted.
    */
  protected def format(session: Session): Unit = {
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
    drops.toBlocking.foreach { drop => drop.await }
  }

  /** Delete a table (and all of the data in the table) from the session's current keyspace
    */
  private def dropTable(session: Session, tableName: String) // format: OFF
      (implicit execution: ExecutionContext): Future[Unit] = { // format: ON
    val dropTable = s"DROP TABLE IF EXISTS $tableName"
    session.executeAsync(dropTable).toFuture.map { _ => () }
  }

}

trait CassandraReaderWriter extends CassandraStoreReader with CassandraStoreWriter

trait CassandraStoreWriter extends ConfiguredCassandra with WriteableStore with Log {
  def writeNotifier: WriteNotifier

  this.session // trigger creating connection, and create schemas if necessary

  /** return a column from a fooSet/barSet/columName path */
  def writeableColumn[T: CanSerialize, U: CanSerialize](columnPath: String): Future[WriteableColumn[T, U]] = {
    val (dataSetName, columnName) = Store.setAndColumn(columnPath)
    SparseColumnWriter.instance[T, U](dataSetName, columnName, columnCatalog,
      dataSetCatalog, writeNotifier)
  }

  /** Create the tables using the session passed.
    * The session's keyspace itself must already exist.
    * Any existing tables are deleted.
    *
    * This call is synchronous.
    */
  def format(): Unit = {
    format(session)
  }

}

/** a Storage data access object for Cassandra. Supports the Store and WriteableStore apis for
  * reading/writing columns and column metadata.
  */
trait CassandraStoreReader extends ConfiguredCassandra with Store with Log {
  def writeListener: WriteListener

  this.session // trigger creating connection, and create schemas if necessary 

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
    val futureColumn = SparseColumnReader.instance[T, U](dataSetName, columnName,
      columnCatalog, writeListener)
    futureColumn
  }

}
