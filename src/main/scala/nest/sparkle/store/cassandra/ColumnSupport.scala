package nest.sparkle.store.cassandra

/** Common code for creating cassandra Column Reader and Writer implementations */
trait ColumnSupport {
  def dataSetName:String
  def columnName:String
  
  lazy val columnPath = dataSetName + "/" + columnName 
  protected val rowIndex = 0.asInstanceOf[AnyRef] // LATER implement when we do wide row partitioning

}