package nest.sparkle.datastream
import org.scalacheck.Gen.choose
import org.scalacheck.Gen
import rx.lang.scala.Observable
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import nest.sparkle.util.ReflectionUtil
import spire.math._
import spire.implicits._

/** Utilities for generating DataStreams (for tests)
 */
object StreamGeneration {
  /** split a sequence into three parts, where the part sizes are randomly generated */
  def threeParts[T](seq:Seq[T]):Gen[Seq[Seq[T]]] = {
    val length = seq.length
    for {
      firstSplit <- choose(0, length)  
      secondSplit <- choose(firstSplit, length)
    } yield {
      val first = seq.slice(0, firstSplit)
      val second = seq.slice(firstSplit, secondSplit)
      val third = seq.slice(secondSplit, length)
      Seq(first, second, third)
    }
  }

  /** create a DataStream from a sequence of tuple sequences */
  def createStream[K: TypeTag, V: TypeTag] // format: OFF
      ( blocks: Seq[Seq[(K,V)]] )
      : DataStream[K,V] = { // format: ON
    
    implicit lazy val keyClassTag = ReflectionUtil.classTag[K](typeTag[K])
    implicit lazy val valueClassTag = ReflectionUtil.classTag[V](typeTag[V])

    val arrays = blocks.map { keyValues =>
      DataArray.fromPairs(keyValues)
    }
    DataStream(Observable.from(arrays))
  }

  /** add up the values in a sequence of key value tuples, 
    * returning None if the sequence is empty */
  def sumValues[K,V:Numeric](keyValues:Seq[(K,V)]):Option[V] = {
    val values = keyValues.map{ case(k,v) => v}
    if (values.isEmpty) { 
      None 
    } else {
      Some(values.reduce{ (a,b) => a + b} )
    }
  }


}