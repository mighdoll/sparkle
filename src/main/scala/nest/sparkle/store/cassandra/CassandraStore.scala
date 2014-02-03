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

import scala.concurrent.{ ExecutionContext, Future }
import scala.language._
import com.datastax.driver.core.{ Cluster, Session }
import nest.sparkle.util.RandomUtil
import com.datastax.driver.core.PreparedStatement
import rx.lang.scala.Observable
import nest.sparkle.util.ObservableFuture._
import nest.sparkle.store.Event
import nest.sparkle.store.Storage
import nest.sparkle.store.DataSet
import nest.sparkle.store.DataSet
import nest.sparkle.store.Column
import com.typesafe.config.Config
import scala.collection.JavaConverters._
import nest.sparkle.store.cassandra.ObservableResultSet._
import spray.util._
import nest.sparkle.store.WriteableStorage

case class AsciiString(val string: String) extends AnyVal
case class NanoTime(val nanos: Long) extends AnyVal
case class MilliTime(val millis: Long) extends AnyVal

object CassandraStore {
  /** return a Storage DAO for cassandra.  */
  def apply(contactHost: String) = new CassandraWithHost(contactHost)

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
}

/** a cassandra store data access layer configured by a config file */
class ConfiguredCassandra(config: Config) extends CassandraStore {
  val storeConfig = config.getConfig("sparkle-store-cassandra")
  override val contactHosts: Seq[String] = storeConfig.getStringList("contactHosts").asScala.toSeq
  override val defaultKeySpace = storeConfig.getString("keySpace")
}

/** a cassandra store data access layer configured with a single contact host parameter */
class CassandraWithHost(contactHost: String) extends CassandraStore {
  override val contactHosts = Seq(contactHost)
}

/** a Storage DAO for cassandra.  */
trait CassandraStore extends Storage with WriteableStorage {
  lazy val catalog = ColumnCatalog(session)
  def contactHosts: Seq[String]
  val defaultKeySpace = "events"    // TODO get rid of this and rely on the .conf default
  implicit def execution: ExecutionContext = ExecutionContext.global

  /** create a connection to the cassandra cluster */
  implicit lazy val session: Session = {
    val builder = Cluster.builder()
    contactHosts.foreach{ builder.addContactPoint(_) }
    val cluster = builder.build()
    val session = cluster.connect()
    useDefaultKeySpace(session)
    session
  }

  /** close the connection to cassandra.  */
  def close() = session.shutdown()

  /** Erase the column store, and recreate the core tables (synchronously: does not return until complete) */
  def format(name: Option[String]) {
    val keySpace = name.getOrElse(defaultKeySpace)
    session.execute(s"""
        DROP KEYSPACE IF EXISTS $keySpace"""
    )
    createKeySpace(session, keySpace)
    catalog.create()
  }

  /** Use the specified C* keyspace for subsequent operations */
  def useKeySpace(keySpace: String = "events") {
    session.execute(s"USE $keySpace")
  }

  /** return the dataset for the provided dataSet  path (fooSet/barSet/mySet).  */
  def dataSet(dataSetPath: String): Future[DataSet] = ???

  /** return a column from a fooSet/barSet/columName path */
  def column[T, U](columnPath: String): Future[Column[T, U]] = {
    val (dataSetName, columnName) = setAndColumn(columnPath)
    val column = SparseColumnReader[T, U](dataSetName, columnName, session, catalog)
    Future.successful(column)
  }

  /** return a column from a fooSet/barSet/columName path */
  def writeableColumn[T: CanSerialize, U: CanSerialize](columnPath: String): Future[WriteableColumn[T, U]] = {
    val (dataSetName, columnName) = setAndColumn(columnPath)
    val column = SparseColumnWriter[T, U](dataSetName, columnName, session, catalog)
    Future.successful(column)
  }
  
  /** Make sure the default keyspace exists, creating it if necessary, and set the cassandra driver 
   *  session to use the default keyspace.  */
  private def useDefaultKeySpace(session: Session) {
    val keySpacesRows = session.executeAsync(s"""
        SELECT keyspace_name FROM system.schema_keyspaces""").observerableRows

    val keySpaces = keySpacesRows.toFutureSeq.await
    val defaultFound = keySpaces.map { _.getString(0) }.find { keySpace =>
      keySpace == defaultKeySpace
    }
    defaultFound.orElse { createKeySpace(session, defaultKeySpace); None }
    val result = session.execute(s"USE $defaultKeySpace") // Consider, what if the db is new and the keyspace is not yet created?    
  }


  /** create a keyspace (db) in cassandra */
  private def createKeySpace(session: Session, keySpace: String) {
    session.execute(s"""
        CREATE KEYSPACE $keySpace
        with replication = {'class': 'SimpleStrategy', 'replication_factor': 1}"""
    )
    session.execute(s"USE $keySpace")
    SparseColumnWriter.createColumnTables(session).await
  }

  /** split a columnPath into a dataSet and column components */
  private def setAndColumn(columnPath: String): (String, String) = {
    val separator = columnPath.lastIndexOf("/")
    val dataSetName = columnPath.substring(0, separator)
    val columnName = columnPath.substring(separator + 1)
    assert (dataSetName.length > 0)
    assert (columnName.length > 0)
    (dataSetName, columnName)
  }

}
