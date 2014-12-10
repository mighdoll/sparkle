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
package nest.sparkle.loader.kafka

import com.typesafe.config.Config
import nest.sparkle.loader._
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen
import org.scalacheck.Gen.Parameters

import scala.collection.{mutable, SortedSet}
import scala.math.Ordering
import scala.reflect.runtime.universe._

import nest.sparkle.store.Event
import nest.sparkle.util.ScalaCheckUtils.withSize


case class MillisDoubleTSV(id1: String, id2: String, key: Long, value: Double)


object MillisDoubleTSVGenerators {

  /** generate a list of long-double MillisDoubleTSV records,
    * along with the original data for verification
    */
  def genRecords(records: Int): Gen[List[MillisDoubleTSV]] =
    Gen.containerOfN[List, MillisDoubleTSV](records, genMillisDoubleTSVRecord)

  /** generate a single MillisDoubleTSV record containing a long-double sample,
    * and associated with a generated set of random ids
    */
  private val genMillisDoubleTSVRecord: Gen[MillisDoubleTSV] = for {
    id1 <- Gen.resize(4, genAlphaNumString)
    id2 <- Gen.resize(4, genAlphaNumString)
    time <- arbitrary[Long]
    value <- arbitrary[Double]
  } yield {
    MillisDoubleTSV(id1, id2, time, value)
  }

  /** Return a sequence of latency records. Each item(sample) has a unique timestamp, values
    *  are random.
    *
    *  @param id1 first per record variable id in the columnPath  (e.g. userID)
    *  @param id2 second per record variable id in the columnPath (e.g. deviceID)
    *  @param records number of records to generate
    */
  def manyRecords(id1: String, id2: String, size: Int, records: Int): Seq[MillisDoubleTSV] = {
    // TODO: Fix loss of using size!
    val eventTuples = latencyEvents.apply(withSize(records)).get

    eventTuples.map{event : (Long,Double) =>
      val (key, value) = event
      MillisDoubleTSV(id1, id2, key, value)
    }.toSeq.sortWith(_.key < _.key)
  }

  /** generate a sorted sequence of key,value pairs with
    *  unique keys. The sequence will
    *  be sized to the full size specified by the size Parameter (it
    *  will not be randomly sized.)
    */
  lazy val latencyEvents: Gen[SortedSet[Tuple2[Long, Double]]] =
    Gen.parameterized { params =>
      val times = mutable.Set[Long]()
      while (times.size < params.size) {
        val time: Long = arbitrary[Long].apply(params).get
        times += time
      }

      val events: Seq[(Long, Double)] = times.toSeq.map { (_, pickDouble()) }

      SortedSet(events: _*)(OrderByFirst())
    }

  /** generate a random alpha numeric string of specified length */
  lazy val genAlphaNumString: Gen[String] = Gen.sized { length =>
    Gen.containerOfN[List, Char](length, Gen.alphaNumChar).map(_.mkString)
  }

  /** order by the first element in a case class */
  private case class OrderByFirst[T: Ordering, V]() extends Ordering[Tuple2[T, V]] {
    def compare(a: Tuple2[T, V], b: Tuple2[T, V]): Int =
      Ordering[T].compare(a._1, b._1)
  }

  /** return an arbitrary double, according to scalacheck's distribution
    *  which includes random values, but is biased to include 0, -1, max, min preferentially
    */
  private def pickDouble(): Double = {
    val params = Parameters.default
    arbitrary[Double].apply(params).get
  }
}


object MillisDoubleTSVSerde extends SparkleSerializer[MillisDoubleTSV] {
  def fromBytes(bytes: Array[Byte]) : MillisDoubleTSV = {
    val str = new String(bytes)
    val valArray = str.split("\t", 4)
    val (id1, id2, key, value) = (valArray(0), valArray(1), valArray(2), valArray(3))

    MillisDoubleTSV(id1, id2, key.toLong, value.toDouble)
  }
  def toBytes(data: MillisDoubleTSV): Array[Byte] = {
    val str = data.id1 + "\t" + data.id2 + "\t" + data.key.toString + "\t" + data.value.toString
    str.getBytes
  }
}


object MillisDoubleTSVDecoder {
  def decoder: ArrayRecordDecoder[MillisDoubleTSV] = {
    //name: String, typed: TypeTag[_]
    val meta = ArrayRecordMeta( Seq(NameTypeDefault("value", typeTag[Double])),
                                NameTypeDefault("time", typeTag[Long]),
                                Seq(NameTypeDefault("id1", typeTag[String]), NameTypeDefault("id2", typeTag[String])))

     ArrayRecordDecoder(decoderFn, meta)
  }

  private def decoderFn : MillisDoubleTSV => ArrayRecordColumns = {
    (record: MillisDoubleTSV) => {
      val ids = Seq(Option(record.id1), Option(record.id2))
      val event = Event[Long,Double](record.key, record.value)

      ArrayRecordColumns(ids, Seq(Seq(event)))
    }
  }
}

object MillisDoubleTSVFinder {
  val id2Default = "id-missing"
}


class MillisDoubleTSVFinder(rootConfig:Config) extends FindDecoder {
  def decoderFor(topic:String):KafkaColumnDecoder[ArrayRecordColumns] = {

    val decoder = MillisDoubleTSVDecoder.decoder

    KafkaKeyValueColumnDecoder(MillisDoubleTSVSerde, decoder, suffix = "Latency", prefix = "sample-data/path")
  }
}
