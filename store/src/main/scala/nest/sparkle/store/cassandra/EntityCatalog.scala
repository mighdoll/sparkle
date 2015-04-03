package nest.sparkle.store.cassandra

import scala.collection.JavaConverters._
import scala.concurrent.{Future, ExecutionContext}
import scala.language.existentials
import scala.util.{Try, Failure, Success}

import com.datastax.driver.core.{Session, PreparedStatement}
import com.typesafe.config.Config

import spray.caching.LruCache

import nest.sparkle.store.{Entity, ColumnPathFormat}
import nest.sparkle.util.GuavaConverters._
import nest.sparkle.util.Log
import nest.sparkle.util.TryToFuture._

case class EntityCatalogStatements(addEntityLookup: PreparedStatement,
                                   lookupEntities: PreparedStatement)

/** Stores entity metadata (where entity refers to the user, device,
  * etc specific info) about a column path. An entity path and associated
  * lookup keys are derived from a column path. Lookup key to entity path
  * mappings are stored in Cassandra. */
case class EntityCatalog(sparkleConfig: Config, columnPathFormat: ColumnPathFormat,
                         session: Session, cassandraConsistency: CassandraConsistency)
  extends PreparedStatements[EntityCatalogStatements] with Log
{
  val tableName = EntityCatalog.tableName

  val addEntityLookupStatement = s"""
      INSERT INTO $tableName
      (lookupKey, entityPath)
      VALUES (?, ?);
    """

  val lookupEntitiesStatement = s"""
      SELECT entityPath FROM $tableName
      WHERE lookupKey = ? ORDER BY entityPath ASC;
    """

  lazy val catalogStatements: EntityCatalogStatements = {
    preparedStatements(makeStatements)
  }

  /** prepare some cql statements */
  def makeStatements(): EntityCatalogStatements = {
    EntityCatalogStatements(
      session.prepare(addEntityLookupStatement).setConsistencyLevel(cassandraConsistency.write),
      session.prepare(lookupEntitiesStatement).setConsistencyLevel(cassandraConsistency.read)
    )
  }

  /** LRU cache used to track entities that this instance has written
    * to cassandra, so we can avoid duplicate writes */
  val entityCatalogCache = LruCache[Unit](sparkleConfig.getConfig("sparkle-store-cassandra")
    .getInt("entity-catalog-max-cache-size"))

  /** clears the entity catalog cache */
  def format() = {
    entityCatalogCache.clear()
  }

  /** An entity path and associated lookup keys are derived from a column path.
    * This metadata is stored to Cassandra. */
  def addColumnPath(columnPath: String)
      (implicit executionContext: ExecutionContext): Future[Unit] = {
    columnPathFormat.entity(columnPath) match {
      case Success(entity)    => addEntity(entity)
      case Failure(exception) => Future.failed(exception)
    }
  }

  /** cache entity catalog entries that this instance has written to cassandra,
    * so we can avoid duplicate writes */
  private def addEntity(entity: Entity)
      (implicit executionContext:ExecutionContext): Future[Unit] = entityCatalogCache(entity.entityPath) { // format: ON
    addEntityToCassandra(entity)
  }

  /** add an entity to cassandra */
  private def addEntityToCassandra(entity: Entity)
      (implicit executionContext: ExecutionContext): Future[Unit] = {
    val results = entity.lookupKeys.map { lookupKey =>
      val statement = catalogStatements.addEntityLookup.bind(lookupKey, entity.entityPath)
      session.executeAsync(statement).toFuture.map { _ =>}
    }
    results.foldLeft(Future.successful(())) { (a, b) =>
      a.flatMap(_ => b)
    }
  }

  /** return the entity paths associated with the given lookup key */
  def lookupEntities(lookupKey: String) // format: OFF
      (implicit executionContext: ExecutionContext): Future[Seq[String]] = { // format: ON
    val statement = catalogStatements.lookupEntities.bind(lookupKey)
    // result will be zero or more rows
    for {
      resultSet <- session.executeAsync(statement).toFuture
      rows <- Try(resultSet.all()).toFuture
    } yield {
      rows.asScala.map { row =>
        row.getString(0)
      }
    }
  }
}

object EntityCatalog {

  val tableName = "entity_catalog"

  /** Create the table using the session passed.
    *
    * @param session Session to use.
    */
  def create(session: Session): Unit = {
    session.execute(s"""
      CREATE TABLE IF NOT EXISTS $tableName (
        lookupKey ascii,
        entityPath ascii,
        PRIMARY KEY(lookupKey, entityPath)
      );"""
    )
  }

}
