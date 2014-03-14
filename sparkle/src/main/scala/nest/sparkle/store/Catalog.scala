package nest.sparkle.store

import scala.concurrent.Future

/**
 * For a Store that has a catalog.
 */
trait CatalogStore {
  val catalog: Catalog
}

/**
 * A catalog for a store.
 */
trait Catalog {
  
  def create(): Unit

  //def writeCatalogEntry(entry: CatalogEntry): Future[Unit]
}

/**
 * An entry in the catalog.
 * Contains metadata about a column of data. 
 * 
 * Store implementations may subclass to add fields.
 * 
 * @param columnPath name of the det and column, e.g. "server1.responseLatency/p99"
 * @param description description of the column (for developer UI documentation)
 * @param domainType type of domain elements, e.g. "NanoTime" for nanosecond timestamps
 * @param rangeType type of range elements, e.g. "Double" for double event
 */
class CatalogEntry(val columnPath: String, val description: String,
                   val domainType: String, val rangeType: String) {
}

object CatalogEntry {
  def apply(columnPath: String, description: String, domainType: String, rangeType: String) = {
    new CatalogEntry(columnPath, description, domainType, rangeType)
  }
  
  def unapply(input: CatalogEntry) = {
    Some(input.columnPath, input.description, input.domainType, input.rangeType)
  }
}
