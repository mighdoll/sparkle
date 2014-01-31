package nest.sparkle.store.ram

import nest.sparkle.store.Storage
import scala.concurrent.Future
import nest.sparkle.store.DataSet
import nest.sparkle.store.Column
import scala.collection.mutable
import nest.sparkle.util.OptionConversion._
import java.io.FileNotFoundException
import nest.sparkle.store.WriteableStorage
import nest.sparkle.store.cassandra.CanSerialize
import nest.sparkle.store.cassandra.WriteableColumn
import scala.reflect.runtime.universe._

class WriteableRamStore extends Storage {
  val columns = mutable.Map[String, RamColumn[_, _]]()

  /** return the dataset for the provided dataSet name or path (fooSet/barSet/mySet).  */
  def dataSet(name: String): Future[DataSet] = ???

  /** return a column from a columnPath e.g. "fooSet/barSet/columName". */
  def column[T, U](columnPath: String): Future[Column[T, U]] = {
    val optTypedColumn = columns.get(columnPath).map { _.asInstanceOf[Column[T, U]] }
    optTypedColumn.toFutureOr(new FileNotFoundException())
  }

  def writeableColumn[T: TypeTag: Ordering, U: TypeTag](columnPath: String) // format: OFF
      : Future[WriteableColumn[T, U]] = { // format: ON
    val ramColumn = new WriteableRamColumn[T, U]("foo")
    columns += columnPath -> ramColumn
    Future.successful(ramColumn)
  }
}