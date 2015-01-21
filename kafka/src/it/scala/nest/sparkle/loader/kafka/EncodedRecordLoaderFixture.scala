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

import java.nio.file.{ Files, Paths }

import scala.collection.JavaConverters._

import kafka.serializer.{ DefaultEncoder, Encoder }
import nest.sparkle.loader.kafka.KafkaTestUtil.testTopicName
import nest.sparkle.store.Event
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.util.ConfigUtil.sparkleConfigName
import nest.sparkle.util.ConfigUtil.modifiedConfig
import nest.sparkle.util.Resources
import spray.util.pimpFuture

trait EncodedRecordLoaderFixture extends CassandraStoreTestConfig {

  override def testConfigFile = Some("sparkle-kafka-tests")

  /** Call a test function after stream loading some data from avro
    *
    * @param recordTypeName the record file to use in the recordFolder
    * @param recordFolder the folder in the resources path from which to read .avro data files
    * @param loadColumnPath the path to read from the database after it has been stream loaded
    * @param records the number of .avro data files to read
    * @param debugPrint set to true to print out the .avro data as it is read
    */
  def withEncodedRecordLoadedColumn[T, U](recordTypeName: String,
                                          recordFolder: String,
                                          loadColumnPath: String,
                                          records: Int = 1,
                                          debugPrint: Boolean = false) // format: OFF
                                          (fn: Seq[Event[T, U]] => Unit) { // format: ON

    val topicName = testTopicName(recordTypeName)
    val kafkaWriter = {
      val encoder: Encoder[Array[Byte]] = new DefaultEncoder
      KafkaWriter(topicName, rootConfig)(encoder)
    }

    /** read one or more avro records from the resources folder */
    def readEncodedRecords(): Seq[Array[Byte]] = {
      def readOneRecord(index: Int): Array[Byte] = {
        val record1Path = Resources.filePathString(s"$recordFolder/$recordTypeName.$index.avro")
        Files.readAllBytes(Paths.get(record1Path))
      }

      (1 to records).map { index =>
        val recordBytes = readOneRecord(index)
        recordBytes
      }
    }

    val modifiedRootConfig = {
      val overrides = Seq(s"$sparkleConfigName.kafka-loader.topics" -> List(topicName).asJava)
      modifiedConfig(rootConfig, overrides: _*)
    }

    withTestDb { testStore =>
      import testStore.execution

      val recordsecords = readEncodedRecords()
      kafkaWriter.write(recordsecords)
      withTestActors { implicit system =>
        val storeWrite = testStore.writeListener.listen(loadColumnPath)
        val loader = new KafkaLoader[Long](modifiedRootConfig, testStore)
        try {
          loader.start()
          storeWrite.take(records).toBlocking.head // await completion of write to store.  
          val readColumn = testStore.column[T, U](loadColumnPath).await
          val read = readColumn.readRange(None, None)
          val results = read.initial.toBlocking.toList
          fn(results)
        } finally {
          loader.shutdown()
        }
      }
    }
  }

}