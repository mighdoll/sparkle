package nest.sparkle.test

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import nest.sparkle.util.{FlexibleConfig, ConfigUtil, LogUtil, Log}
import nest.sparkle.util.ConfigUtil.sparkleConfigName
import org.scalatest.Suite
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.file.Files
import java.nio.file.Paths
import scala.collection.JavaConverters._
import java.io.BufferedWriter
import java.io.PrintWriter

trait SparkleTestConfig extends FlexibleConfig with Suite with BeforeAndAfterAll with Log{

  override def beforeAll() {
    initializeLogging()
    super.beforeAll()
  }

  /** setup debug logging for tests (via sl4fj).  */
  def initializeLogging(): Unit = {
    rootConfig // reference the lazy val rootConfig to trigger it to evaluate 
  }


}
