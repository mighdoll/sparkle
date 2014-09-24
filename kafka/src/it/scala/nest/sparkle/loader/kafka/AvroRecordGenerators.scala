/* Copyright 2014  Nest Labs

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
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Arbitrary
import org.apache.avro.generic.GenericData
import java.util.ArrayList
import scala.collection.SortedSet
import org.scalacheck.Gen
import nest.sparkle.store.Event
import scala.math.Ordering
import org.scalacheck.Gen.Parameters
import nest.sparkle.util.ScalaCheckUtils.withSize
import scala.collection.mutable


/** test generator support for making milli-double GenericRecord avro data */
object AvroRecordGenerators {

  /** generate a list of long-double avro array latency records,
    * along with the original data for verification
    */
  def genArrayRecords(records: Int): Gen[List[GeneratedRecord[Long, Double]]] =
    Gen.containerOfN[List, GeneratedRecord[Long, Double]](records, genMillisDoubleArrayRecord)

  val genArrayRecords2 = Gen.sized { size =>
    Gen.containerOfN[Seq, GeneratedRecord[Long, Double]](size, genMillisDoubleArrayRecord)
  }

  /** a single generated record encoded in Avro, along with the source data used to construct it
    * (the source data is easier to use for validating results based on the generated record)
    */
  case class GeneratedRecord[T, U](id1: String, id2: String,
                                   events: SortedSet[(T, U)],
                                   record: GenericData.Record)

  object Implicits {
    /** generate a single long-double (non-array) latency record */
    implicit val arbitraryMillisDoubleRecord: Arbitrary[GenericData.Record] =
      Arbitrary(genMillisDoubleRecord)
  }

  /** return a single GenericData record containing an array of latency time,value events
    * Note the id2 may be null.
    */
  def makeLatencyRecord(id1: String, id2: String,
                        events: Iterable[(Long, Double)]): GenericData.Record = {
    val elementArray = {
      val collection = new ArrayList[GenericData.Record]()
      val array = new GenericData.Array(MillisDoubleArrayAvro.arraySchema, collection)
      events.foreach {
        case (time, value) =>
          val element = new GenericData.Record(MillisDoubleArrayAvro.elementSchema)
          element.put("time", time)
          element.put("value", value)
          array.add(element)
      }
      array
    }

    val latencyRecord = {
      val record = new GenericData.Record(MillisDoubleArrayAvro.schema)
      record.put("id1", id1)
      record.put("id2", id2) // id2 field could be null in Avro record
      record.put("elements", elementArray)
      record
    }

    latencyRecord
  }

  /** generate a single (non-array) long-double latency record */
  private val genMillisDoubleRecord = for {
    time <- arbitrary[Long]
    value <- arbitrary[Double]
    id <- arbitrary[Int]
  } yield {
    val record = new GenericData.Record(MillisDoubleAvro.schema)
    record.put("id", id.toString)
    record.put("time", time)
    record.put("value", value)
    record
  }

  /** generate a single array latency record containing an array of long-double samples, and associated with
    * a generated set of random ids
    */
  private val genMillisDoubleArrayRecord: Gen[GeneratedRecord[Long, Double]] = for {
    id1 <- Gen.resize(4, genAlphaNumString)
    id2 <- Gen.resize(4, genAlphaNumString) // TODO: Make this null sometimes.
    times <- Gen.nonEmptyContainerOf[SortedSet, Long](arbitrary[Long])
    values <- Gen.containerOfN[List, Double](times.size, arbitrary[Double])
    events = times.zip(values).map { case (time, value) => (time, value) }
  } yield {
    val record = makeLatencyRecord(id1, id2, events)
    GeneratedRecord[Long, Double](id1, id2, events, record)
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
  
  /** Return a sequence of latency records. Each item(sample) has a unique timestamp, values
   *  are random.
   *  
   *  @param id1 first per record variable id in the columnPath  (e.g. userID)
   *  @param id2 second per record variable id in the columnPath (e.g. deviceID)
   *  @param size number of items (samples) per avro record
   *  @param records number of records to generate
   */
  def manyRecords(id1: String, id2: String, size: Int, records: Int): Seq[GeneratedRecord[Long, Double]] = {
    val eventTuples = latencyEvents.apply(withSize(records * size)).get
    eventTuples.grouped(size).map { events =>
      val record = makeLatencyRecord(id1, id2, events)
      GeneratedRecord(id1, id2, events, record)
    }.toSeq
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

}
