package nest.sparkle.loader.spark

import com.typesafe.config.Config

import nest.sparkle.loader.Loader.TaggedBlock
import nest.sparkle.shell.SparkConnection
import nest.sparkle.store.WriteableStore
import nest.sparkle.util.{ConfigUtil, Log}

import org.apache.spark.SparkContext
import org.apache.spark.rdd._

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe._

/** Start a loader that will read Avro files from HDFS and load data into cassandra
  * type parameter K is the type of the key in the store, (which is not necessarily the same as the type
  * of keys in the data input)
  * V is the type of the EventReader "value"
  */
class SparkLoader[K: TypeTag, V](rootConfig: Config,
                              context: SparkContext,
                              storage: WriteableStore,
                              sourceReader: EventReader[K, V])
  (implicit execution: ExecutionContext)
  extends Log
{
  private val loaderConfig = ConfigUtil.configForSparkle(rootConfig).getConfig("hdfs-loader")
  private val eventsToLoad = loaderConfig.getStringList("events").asScala.toSeq

  val sparkConnection = new SparkConnection(rootConfig)

  //get intput files
  def createSparkApp: RDD[TaggedBlock] ={
    val empty = sparkConnection.sparkContext.emptyRDD[TaggedBlock]

    val rdds = for (event <- eventsToLoad)
      yield sourceReader.events(event, sparkConnection.sparkContext)

    rdds.foldLeft(empty.asInstanceOf[RDD[TaggedBlock]])((rdd1,rdd2) => rdd1 ++ rdd2)
  }
}
