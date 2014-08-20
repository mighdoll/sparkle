package nest.sparkle.loader.kafka

import java.io.File
import java.nio.file.{Files, Paths}

import org.apache.avro.Schema.Parser

import kafka.serializer.{DefaultEncoder, Encoder}
import nest.sparkle.loader.kafka.KafkaTestUtil.testTopicName
import nest.sparkle.store.Event
import nest.sparkle.store.cassandra.CassandraTestConfig
import nest.sparkle.util.AvroUtil.prettyAvro
import nest.sparkle.util.ConfigUtil.sparkleConfigName
import nest.sparkle.util.ConfigUtil.modifiedConfig
import nest.sparkle.util.Resources
import spray.util.pimpFuture

trait AvroLoaderFixture extends CassandraTestConfig with KafkaTestConfig {
  /** Call a test function after stream loading some data from avro
    *
    * @param schemaFileName the schema file to use in schemaFolder
    * @param schemaFolder the folder in the resources path from which to read .avsc schema files
    * @param recordFolder the folder in the resources path from which to read .avro data files
    * @param loadColumnPath the path to read from the database after it has been stream loaded
    * @param records the number of .avro data files to read
    * @param debugPrint set to true to print out the .avro data as it is read
    */
  def withAvroLoadedColumn[T, U](schemaFolder: String,
                                 schemaFileName: String,
                                 recordFolder: String,
                                 loadColumnPath: String,
                                 records: Int = 1,
                                 debugPrint: Boolean = false) // format: OFF
                              (fn: Seq[Event[T, U]] => Unit) { // format: ON
    val schema = {
      val schemaPath = Resources.filePathString(s"$schemaFolder/$schemaFileName.avsc")
      val parser = new Parser
      parser.parse(new File(schemaPath))
    }

    val topicName = testTopicName(schemaFileName)
    val kafkaWriter = {
      val encoder: Encoder[Array[Byte]] = new DefaultEncoder
      KafkaWriter(topicName, rootConfig)(encoder)
    }

    /** read one or more avro records from the resources folder */
    def readAvroRecords(): Seq[Array[Byte]] = {
      def readOneRecord(index: Int): Array[Byte] = {
        val record1Path = Resources.filePathString(s"$recordFolder/$schemaFileName.$index.avro")
        Files.readAllBytes(Paths.get(record1Path))
      }

      (1 to records).map { index =>
        val recordBytes = readOneRecord(index)
        if (debugPrint) {
          val recordString = prettyAvro(recordBytes, schema)
          println(s"$recordString")
        }
        recordBytes
      }
    }

    import scala.collection.JavaConverters._
    val modifiedRootConfig = {
      val overrides = Seq(s"$sparkleConfigName.kafka-loader.topics" -> List(topicName).asJava)
      modifiedConfig(rootConfig, overrides: _*)
    }

    withTestDb { testStore =>
      import testStore.execution

      val avroRecords = readAvroRecords()
      kafkaWriter.write(avroRecords)
      val storeWrite = testStore.writeListener.listen[T](loadColumnPath)
      val loader = new AvroKafkaLoader[Long](modifiedRootConfig, testStore) 
      storeWrite.take(records).toBlocking.head // await completion of write to store.  
      
      val readColumn = testStore.column[T, U](loadColumnPath).await
      val read = readColumn.readRange(None, None)
      val results = read.initial.toBlocking.toList
      fn(results)
    }
  }

}