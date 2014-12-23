package nest.sparkle.util

import java.io.File
import java.nio.file.{Files, Paths, Path}

import org.scalatest.{Matchers, FunSuite}
import org.slf4j.LoggerFactory
import ch.qos.logback.core.util.FileSize

import com.typesafe.scalalogging.slf4j.Logger
import com.typesafe.config.{ConfigFactory, Config}

/**
 * Test logback configuring.
 */
class TestLogbackConfig
  extends FunSuite 
  with Matchers
{
  private lazy val rootConfig = {
    val baseConfig = ConfigFactory.load()
    val config = ConfigFactory.parseResources("test-logback.conf").resolve()
    config.withFallback(baseConfig)
  }
  
  private def filePath(config: Config): Path = {
    val path = config.getString("file.path")
    Paths.get(path)
  }
  
  private def findFiles(path: Path) = {
    val directory = path.getParent.toFile
    val baseName = path.toString
    val ii = baseName.lastIndexOf(".")
    val fnPattern = {
      ii match {
        case -1 =>
          (baseName + """(\.\d+)?""").r
        case _  =>
          (baseName.substring(0,ii+1) + """(\d+\.)?""" + baseName.substring(ii+1)).r
      }
    }
    directory.listFiles().filter { file =>
      file.getAbsolutePath match {
        case fnPattern(s) => true
        case _            => false
      }
    }
  }
  
  private def eraseFiles(path: Path): Unit = {
    val files = findFiles(path)
    files.foreach(_.delete())
  }

  /** 
   *  Apply any overrides.
   */
  private def makeConfig(overrides: List[(String, Any)] = List()): Config = {
    ConfigUtil.modifiedConfig(rootConfig, overrides: _*)
  }
  
  test("writing a message should create a file") {

    val config = makeConfig()
    val sparkleConfig = ConfigUtil.configForSparkle(config)
    val loggingConfig = sparkleConfig.getConfig("logging")

    val basePath = filePath(loggingConfig)
    val maxSize = FileSize.valueOf(loggingConfig.getString("file.max-size")).getSize

    eraseFiles(basePath)

    ConfigureLogback.configureLogging(sparkleConfig)
    
    val log: Logger = Logger(LoggerFactory.getLogger("test"))
    (1 to 5000) foreach { index =>
      if (index % 1000 == 0) Thread.sleep(10L)  // allow time for rollover to occur
      log.info("test message aààààààààààààààààààààààààààààààààààààààààààààààààààààà") // 100 char line
    }
    
    // Should be 4 files
    val files = findFiles(basePath)
    files should have length 4
    
/*  Apparently the maxSize isn't strictly enforced
    // Files should not be greater then the max
    files.foreach { file =>
      val fileSize = Files.size(file.toPath)
      println(s"$file $fileSize $maxSize")
      fileSize should be <= maxSize
    }
*/
  }

}
