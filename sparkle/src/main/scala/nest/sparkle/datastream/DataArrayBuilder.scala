package nest.sparkle.datastream

import scala.collection.mutable.{ArrayBuffer, Builder}
import scala.reflect.ClassTag


/** A Builder implementation for DataArray so it can participate in the scala collections
 *  hierarchy.
 */
class DataArrayBuilder[K: ClassTag, V: ClassTag] extends Builder[(K, V), DataArray[K, V]] {
    self =>
    
  val keys = ArrayBuffer[K]()
  val values = ArrayBuffer[V]()
  
  override def +=(elem: (K, V)): this.type = {
    val (key, value) = elem
    keys += key
    values += value
    this
  }

  override def result(): DataArray[K, V] = {
    DataArray(keys.toArray, values.toArray)
  }

  override def clear(): Unit = {
    keys.clear
    values.clear
  }
  
  // LATER override sizeHint

}