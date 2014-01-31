package nest.sparkle.store

import scala.concurrent.Future

/** a 'folder' that contains other datasets or columns */
trait DataSet {
  /** name of the dataset: legal characters are [a-ZA-Z0-9_-. ]+ */
  def name: String

  /** return a column in this dataset (or FileNotFound) */
  def column[T, U](columnName: String): Future[Column[T, U]]

  /** return all child columns */
  def childColumns: Future[Iterable[String]]

  /** return all child datasets */
  def childDataSets: Future[Iterable[DataSet]] = ??? // LATER
}
