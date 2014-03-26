package nest.sparkle.store.ram

import scala.concurrent.Future

import rx.lang.scala.Observable

import nest.sparkle.store.{Store, DataSet, Column}

/**
 * DataSet stored in RAM.
 */
case class RamDataSet(store: WriteableRamStore, name: String) extends DataSet {
  
  /** return a column in this dataset (or FileNotFound) */
  def column[T, U](columnName: String): Future[Column[T, U]] = {
    ???
  }

  /** 
   * return the names of all child columns 
   * 
   * @return Observable of full path of any child columns of this DataSet.
   */
  def childColumns: Observable[String] = {
    Observable.from(store.dataSetColumnPaths(name))
  }

  /** 
   * return all child datasets 
   * 
   * @return Observable of any child DataSets of this DataSet.
   */
  def childDataSets: Observable[DataSet] = {
    ???
  }
}
