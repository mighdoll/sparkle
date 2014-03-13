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
  
  object Implicits {
    implicit val arbitraryMillisDoubleRecord = Arbitrary(genMillisDoubleRecord)
  }
}
