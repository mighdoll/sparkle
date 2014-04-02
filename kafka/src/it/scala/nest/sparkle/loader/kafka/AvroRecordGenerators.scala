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

/** test generator support for making milli-double GenericRecord avro data */
object AvroRecordGenerators {
  /** generate a list of long-double array latency records */
  def genArrayRecords(records: Int):Gen[List[GeneratedRecord[Long,Double]]] =
    Gen.containerOfN[List, GeneratedRecord[Long, Double]](records, genMillisDoubleArrayRecord)

  /** a single generated record encoded in Avro, along with the source data used to construct it
   *  (the source data is easier to use for validating results based on the generated record) */
  case class GeneratedRecord[T, U](id: String, events: SortedSet[(Long, Double)], record: GenericData.Record)

  object Implicits {
    /** generate a single long-double (non-array) latency record */
    implicit val arbitraryMillisDoubleRecord:Arbitrary[GenericData.Record] = 
      Arbitrary(genMillisDoubleRecord)
  }
  
  /** return a single GenericData record containing an array of latency time,value events */
  def makeLatencyRecord(id: String, events: Iterable[(Long, Double)]): GenericData.Record = {
    val elementArray = {
      val collection = new ArrayList[GenericData.Record]()
      val array = new GenericData.Array(MillisDoubleArrayAvro.arraySchema, collection)
      events.foreach{
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
      record.put("id", id)
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

  /** generate a single latency record containing an array of long-double samples */
  private val genMillisDoubleArrayRecord: Gen[GeneratedRecord[Long, Double]] = for {
    id <- genAlphaNumString(4)
    times <- Gen.nonEmptyContainerOf[SortedSet, Long](arbitrary[Long])
    values <- Gen.containerOfN[List, Double](times.size, arbitrary[Double])
    events = times.zip(values).map { case (time, value) => (time, value) }
  } yield {
    val record = makeLatencyRecord(id.toString, events)
    GeneratedRecord(id.toString, events, record)
  }

  /** generate a random alpha numeric string of specified length */
  private def genAlphaNumString(length: Int): Gen[String] =
    Gen.containerOfN[List, Char](length, Gen.alphaNumChar).map (_.mkString)


}
