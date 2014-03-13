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

import org.scalatest.FunSuite
import org.scalatest.Matchers
import scala.concurrent.ExecutionContext
import org.apache.avro.generic.GenericData
import org.scalatest.prop.PropertyChecks
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import scala.collection.JavaConverters._
import nest.sparkle.util.ConfigUtil.modifiedConfig
import nest.sparkle.store.cassandra.ConfiguredCassandra

class TestStreamLoadAvroKafka extends FunSuite with Matchers with PropertyChecks
with KafkaTestConfig with CassandraTestConfig {

  val testStore = new ConfiguredCassandra(cassandraConfig)
  ignore("stream load a few milliDouble samples") {
    import ExecutionContext.Implicits.global
    import AvroRecordGenerators.Implicits.arbitraryMillisDoubleRecord
    
    forAll(MinSuccessful(1)) { records: List[GenericData.Record] =>
      whenever(records.length > 0) {
        KafkaTestUtil.withTestAvroTopic(loaderConfig) { kafka =>
          val overrides =
            "topics" -> List(kafka.topic) ::
              "schema-finder" -> "nest.sparkle.loader.kafka.MillisDoubleSchemaFinder" ::
              Nil

          val config = modifiedConfig(loaderConfig, overrides: _*)

          kafka.writer.write(records)
          val loader = new AvroKafkaLoader(config, testStore)

          // TODO read from cassandra and verify that they're there
        }
      }
    }
  }
}
