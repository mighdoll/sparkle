package nest.sparkle.store

import nest.sparkle.store.cassandra.CanSerialize
import scala.concurrent.Future
import nest.sparkle.store.cassandra.WriteableColumn

trait WriteableStorage {
  def writeableColumn[T: CanSerialize, U: CanSerialize](columnPath: String): Future[WriteableColumn[T, U]]
}