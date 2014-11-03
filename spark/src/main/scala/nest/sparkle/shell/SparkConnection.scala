package nest.sparkle.shell

import com.typesafe.config.Config
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import com.datastax.spark.connector._
import com.datastax.spark.connector.rdd.reader._
import com.datastax.spark.connector.rdd.CassandraRDD
import nest.sparkle.util.ConfigUtil
import scala.collection.JavaConverters._
import scala.reflect.runtime.universe._
import nest.sparkle.store.cassandra.ColumnTypes
import nest.sparkle.store.cassandra.RecoverCanSerialize
import nest.sparkle.util.Log
import scala.util.control.Exception._
import org.apache.spark.rdd.RDD
import nest.sparkle.store.Event
import org.apache.spark.rdd.EmptyRDD
import com.datastax.spark.connector.types.TypeConverter
import spray.json.JsValue
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.Try

/** a connection to the spark service, encapsulating the SparkContext and
 *  providing access routines to get RDDs from sparkle columns. */
case class SparkConnection(rootConfig: Config, applicationName: String = "Sparkle") extends Log {
  SparkConnection.initializeConverters()

  val sparkleConfig = ConfigUtil.configForSparkle(rootConfig)

  /** open a connection to cassandra and the spark master */
  lazy val sparkContext: SparkContext = {
    val cassandraHost = {
      // fix after spark-cassandra driver bug #250 (should take multiple hosts)
      val hosts = sparkleConfig.getStringList("sparkle-store-cassandra.contact-hosts")
      hosts.asScala.head
    }

    val sparkConf = new SparkConf(true)
      .set("spark.cassandra.connection.host", cassandraHost)

    val sparkClusterUrl = sparkleConfig.getString("spark.master-url")

    new SparkContext(sparkClusterUrl, applicationName, sparkConf)
  }

  /** Return all the events in a given key,value typed column.
   *   
   *  Note that this doesn't work for 'high-level' types that map to 
   *  primitive columns like String or Bytes. e.g. JsValue column types can't
   *  be safely fetched at present because the underlying cassandra table will
   *  mix all string values in the same table, including some that are not
   *  actually JsValues. */
  def columnsRDD[K: TypeTag, V: TypeTag]: RDD[Event[K, V]] = {
    // the name of the table we're fetching from, e.g. bigint0double
    val tableNameTry =
      for {
        keySerialize <- RecoverCanSerialize.tryCanSerialize[K](implicitly[TypeTag[K]])
        valueSerialize <- RecoverCanSerialize.tryCanSerialize[V](implicitly[TypeTag[V]])
      } yield {
        ColumnTypes.serializationInfo[K, V]()(keySerialize, valueSerialize).tableName
      }

    // tells cassandra Spark connector to to parse the row
    implicit val keyConverter : TypeConverter[K] = TypeConverter.forType[K]
    implicit val valueConverter : TypeConverter[V] = TypeConverter.forType[V]
    implicit val rrf = new KeyValueRowReaderFactory[K,V]( new ValueRowReaderFactory[K], new ValueRowReaderFactory[V] )

    val keyspace = sparkleConfig.getString("sparkle-store-cassandra.key-space")
    val sc = sparkContext  // spark can't serialize sparkContext directly

    val rddTry =
      for {
        tableName <- tableNameTry
        rdd <- nonFatalCatch withTry { sc.cassandraTable[(K,V)](keyspace, tableName).select("argument", "value") }
      } yield {
        rdd.map { row =>
          val (key, value) = row
          Event(key, value)
        }
      }

    rddTry.recover {
      case err =>
        log.error("unable to fetch cassandra rdd", err)
        throw err // TODO return EmptyRDD in spark 1.1+
    }.get

  }

  /** shutdown the spark server */
  def close() {
    sparkContext.stop()
  }
}

object SparkConnection {
  private val initialized = new AtomicBoolean

  /** install type converters for the cassandra-spark-connector for our custom types */
  def initializeConverters() {
    if (initialized.compareAndSet(false, true)) {
      TypeConverter.registerConverter(JsValueConverter)
    }
  }

  /** spark-cassandra typeconverter to read JsValues */
  object JsValueConverter extends TypeConverter[JsValue] {
    import spray.json._
    def targetTypeTag = implicitly[TypeTag[JsValue]]
    def convertPF = {
      case x: String => println(s"parsing: $x"); x.asJson
    }
  }

}
