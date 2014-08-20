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

package nest.sparkle.util

import scala.concurrent.Await
import scala.concurrent.duration._

import com.typesafe.config.Config

import org.clapper.argot.{ArgotParser, ArgotUsageException}
import org.clapper.argot.ArgotConverters._

/** a utility trait for making a main class that uses Argot command line parsing,
  * loads and modifies the configuration, configures logging, and starts
  * metrics reporting.
  */
trait SparkleApp 
  extends App 
{
  val appName: String
  val appVersion: String
  
  val parser = new ArgotParser(appName, preUsage = Some(appVersion))
  val help = parser.flag[Boolean](List("h", "help"), "show this help")
  val confFile = parser.option[String](List("conf"), "path", "path to a .conf file")
  
  /** Override this fcn to return a list of key, values to override in the 
    * configuration 
    */
  def overrides: Seq[(String, Any)] = Seq()
  
  def setup(): Config = {
    parse()
    
    println(s"--conf ${confFile.value}")
    val fileConfig = ConfigUtil.configFromFile(confFile.value)
    val rootConfig = ConfigUtil.modifiedConfig(fileConfig, overrides: _*)
    val sparkleConfig = configForSparkle(rootConfig)
    
    // Configure logging
    LogUtil.configureLogging(rootConfig)

    // Start optional metrics graphite reporting.
    val graphiteConfig = sparkleConfig.getConfig("metrics.graphite")
    if (graphiteConfig.getBoolean("reporter.enable")) {
      MetricsSupport.startGraphiteReporter(graphiteConfig)
    }
    
    // Start an http server to return metrics values in graphite format.
    if (graphiteConfig.getBoolean("collector.enable")) {
      Await.result(MetricsSupport.startCollector(graphiteConfig), 20 seconds)
    }
    
    rootConfig
  }
  
  def configForSparkle(rootConfig: Config): Config = ConfigUtil.configForSparkle(rootConfig)
  
  //lazy val actorSystem = 

  /**
   * Parse the command line parameters.
   * 
   */
  private def parse() {
    try {
      parser.parse(args)

      help.value.foreach { v =>
        Console.println(parser.usageString())
        sys.exit(0)
      }
    } catch {
      case e: ArgotUsageException =>
        println(e.message)
        sys.exit(1)
    }
  }
}
