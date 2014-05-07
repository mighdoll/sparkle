package nest.sparkle.time.transform
import SummaryTransform.Events
import scala.reflect.runtime.universe._


/** superclass to create a SummaryTransform from a function that 
 *  summarizes a single block of event data. */
trait OneSummarizer extends SummaryTransform {
  
  /** summarize a block of data. (subclasses must implement) */
  def summarizeEvents[T, U](data: Events[T, U],
                            columnMetadata: ColumnMetadata[T, U]): Events[T, U]

  /** Share the block summarizer function to the SummaryTransform lifecycle.
   * 
   *  The SummaryTransform will call createPartitionSummarizer when it
   *  is initialized for a given column type, and then use the resulting
   *  block summarizer on each partition of data.
   *  
   *  During initialization, we create a ColumnMetadata object once and 
   *  later make it available to the subclass provided summarizeEvents function 
   *  so that block summarizers can access column type metadata.
   */
  override protected def createPartitionSummarizer[T: TypeTag, U: TypeTag]() = {
    val columnMetadata = ColumnMetadata[T, U]()

    new PartitionSummarizer[T, U] {
      override def summarizePart(data: Events[T, U]): Events[T, U] = {
        summarizeEvents(data, columnMetadata)
      }
    }
  }
}
