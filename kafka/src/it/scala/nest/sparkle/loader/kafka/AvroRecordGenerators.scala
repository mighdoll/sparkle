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

/** test generator support for making millis-double GenericRecord avro data */
object AvroRecordGenerators {
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

  private val genMillisDoubleArrayRecord = for {
    id <- arbitrary[Int]
    events <- arbitrary[List[(Long, Double)]]
  } yield {
    makeLatencyRecord(id.toString, events)
  }

  object Implicits {
    implicit val arbitraryMillisDoubleRecord = Arbitrary(genMillisDoubleRecord)
    implicit val arbitraryMillisDoubleArrayRecord = Arbitrary(genMillisDoubleArrayRecord)
  }

  def makeLatencyRecord(id:String, events: Iterable[(Long, Double)]): GenericData.Record = {
    val elementArray = {
      val collection = new ArrayList[GenericData.Record]()
      val array = new GenericData.Array(MillisDoubleArrayAvro.arraySchema, collection)
      events.foreach{
        case (time, value) =>
          val element = new GenericData.Record(MillisDoubleArrayAvro.elementSchema)
          element.put("time", 1L)
          element.put("value", 13.1)
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

}
