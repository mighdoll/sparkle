/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.legacy

import nest.sparkle.util.Opt
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.collection.mutable
import scala.collection.immutable.VectorBuilder
import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag
import nest.sparkle.legacy.Datum.ValueOrdering
import nest.sparkle.util.Opt._
import scala.language.higherKinds

/** Aggregation functions for an array of Datum objects (mean, min, max, etc.) */
object Summarize {
  private type SummaryFn = (Array[Datum], Int) => Array[Datum]

  import Datum.ValueOrdering

  val summaryMax = makeSummaryFn(a => Array(a.max))
  val summaryMin = makeSummaryFn(a => Array(a.min))
  val summaryMean = makeSummaryFn(mean)
  val summaryRandom = makeSummaryFn(random)
  val summaryUnique = makeSummaryFn(uniques)
  val summarySum = makeSummaryFn(sum)
  val summaryCount = makeSummaryFn(count)
  val summaryRate = makeSummaryFn(ratePerSecond)

  /** Return an aggregation function from a string selector ("min", "max", etc.). */
  def apply(summaryName: Opt[String]): SummaryFn = {
    val summary = summaryName.getOrElse("max")

    summary match {
      case "max"     => summaryMax
      case "min"     => summaryMin
      case "linear"  => summaryMean
      case "mean"    => summaryMean
      case "average" => summaryMean
      case "random"  => summaryRandom
      case "sample"  => summaryRandom
      case "uniques" => summaryUnique
      case "sum"     => summarySum
      case "count"   => summaryCount
      case "rate"    => summaryRate
    }
  }

  import scala.language.higherKinds
  /** remove duplicates when they come in a sequence
   *  (SCALA.  not fully generic, and isn't ought to be an easier way..) */
  private def removeDuplicateRuns[T: ClassTag, C[T] <: Array[T]](coll: C[T]): Array[T] = {
    var prev = coll.headOption
    val markDups = prev +: coll.map { elem =>
      if (Some(elem) != prev) {
        prev = Some(elem)
        prev
      } else {
        None
      }
    }
    markDups.flatten
  }

  /** bucket the results by time */
  private def bucketByTime(data: Array[Datum], buckets: Int): Iterable[Array[Datum]] = {

    // group of data elements all of which end before the 'end' time
    case class Partition(val end: Double, val data: ArrayBuffer[Datum] = ArrayBuffer())

    val start = data.head.time
    val end = data.last.time
    val size = end - start
    val step = (end - start).toDouble / buckets
    var currentPartition = Partition(start + step) // initialize with partition #1

    /** return the partition for a given value, reusing the most recently used partition if possible.
      * We include the last value in the final partition,
      *   e.g. range 0 -> 2 in three partitions is [0-1),[1-2),[2-3].
      * That way asking for 1 partition gets everything in one partition, and all partitions
      * include their start.
      */
    def getPartition(value: Double): Partition = {
      if ((value > currentPartition.end) || (value == currentPartition.end && value != end)) {
        // get the appropriate partition sequence number.  First partition slot is #1, then #2, etc.
        val nthPartition = Math.floor((value - start) / step) + 1
        currentPartition = Partition(start + step * nthPartition)
      }
      currentPartition
    }

    // assign each datum to a partition, and return an array of containing the partition to which each
    // datum is assigned
    val parts:Array[Partition] =
      for {
        datum <- data
        partition = getPartition(datum.time)
      } yield {
        partition.data += datum
        partition
      }

    val uniquePartitions = removeDuplicateRuns(parts)
    val results: Array[Array[Datum]] = uniquePartitions.map{ _.data.toArray }

    results
  }

  /** Create a function to calculate a summary of arbitrary array size given a function that
    * summarizes an array to a smaller array, typically containing one element.
    */
  private def makeSummaryFn(fn: Array[Datum] => Array[Datum]): SummaryFn = {
    (data: Array[Datum], summarySize: Int) =>
      if (data.length > 0) {
        val bucketed = bucketByTime(data, summarySize)
        bucketed.flatMap(bucket => fn(bucket)) toArray
      } else {
        Array()
      }
  }
  
  private def ratePerSecond(data: Array[Datum]) = ???  /* TODO adjust bucketByTime to pass along partition time boundary so we can calculate rate.. */

  /** return the arithmetic mean */
  private def mean(data: Array[Datum]): Array[Datum] = {
    val sum = data.reduceLeft { (a, b) => Datum(a.time + b.time, a.value + b.value) }
    Array(Datum(sum.time / data.length, sum.value / data.length))
  }

  /** return the sum */
  private def sum(data: Array[Datum]): Array[Datum] = {
    val sum = data.reduceLeft { (a, b) => Datum(a.time + b.time, a.value + b.value) }
    Array(Datum(sum.time / data.length, sum.value))
  }

  /** return a count of events */
  private def count(data: Array[Datum]): Array[Datum] = {
    val sum = data.reduceLeft { (a, b) => Datum(a.time + b.time, a.value + b.value) }
    Array(Datum(data.head.time, data.length))
  }

  /** choose a random element */
  private def random(data: Array[Datum]): Array[Datum] = {
    val pick = ThreadLocalRandom.current().nextLong(data.length).toInt
    Array(data(pick))
  }

  /** return unique values in the data set */
  private def uniques(data: Array[Datum]): Array[Datum] = {
    val byValue = mutable.HashMap[Double, VectorBuilder[Long]]()
    data.foreach { datum =>
      val vector = byValue.getOrElseUpdate(datum.value, new VectorBuilder[Long]())
      vector += datum.time
    }

    val uniqueValues =
      byValue.map {
        case (value, times) =>
          Datum(middle(times.result), value)
      }
    uniqueValues.toArray
  }

  /** return the middle value from a sequence.  returns the median if the sequence is sorted */
  private def middle[T](seq: IndexedSeq[T]): T = {
    require(seq.length > 0)
    seq(seq.length / 2)
  }

}
