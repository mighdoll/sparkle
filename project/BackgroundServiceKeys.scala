import sbt._
import scala.util.Try

/** The background service provides a set of keys for launching a project in a background jvm,
 *  and launching related services as well. 
 *  
 *  Projects using the BackgroundService should add BackgroundService.settings to their build settings. 
 *  e.g.
 *        .settings(BackgroundService.settings: _*)
 *  
 *  Projects can set dependencies on other projects that need to be running:
 *          dependenciesToStart := Seq(kafkaServer, cassandraServer),
 *         
 *  Projects should provide a function to check their liveness (e.g. by making a protocol request
 *  to verify the server), and typically set a main class:
 *       .settings(BuildSettings.setMainClass("com.nestlabs.kafka.Main"): _*)
 *          healthCheckFn <<= HealthChecks.kafkaIsHealthy,
 *          
 *  Projects wishing to override the it:test command to start dependent services, use: 
 *         test in IntegrationTest := BackgroundService.itTestTask.value
 *  (someday this may become automatic)
 *  
 *  A set of tasks useful from the sbt command line are also avaiable. 
 * 
 *    
 *  BackgroundService depends on the revolver plugin.
 */
object BackgroundServiceKeys {

  // commands 
  val start = taskKey[Unit]("launch service in background")
  val stop = taskKey[Unit]("stop service")
  val startSolo = taskKey[Unit]("launch service in background. don't launch service dependencies ")
  val stopSolo = taskKey[Unit]("stop service. don't stop service dependencies ")
  val status = TaskKey[Unit]("status", "Status of the service")
  val startDependencies = taskKey[Unit]("start services this service depends on")
  val stopDependencies = taskKey[Unit]("stop all the background services this service depends on")
  val healthCheck = taskKey[Boolean]("Returns 'true' if service is up and healthy, 'false' otherwise")
  val waitUntilHealthy = taskKey[Unit]("Blocks until service is healthy")

  // project build can set these
  val healthCheckFn = taskKey[() => Try[Boolean]]("Returns 'true' if service is up and healthy, 'false' otherwise")
  val dependenciesToStart = settingKey[Seq[Project]]("other service projects to start before running this service")
  val adminPort = SettingKey[Option[Int]]("admin-port", "The TCP port where the admin HTTP for this service runs on")
  val healthPort = SettingKey[Option[Int]]("health-port", "Port to ping via HTTP request to /health to check the health of the service")
  val jmxPort = SettingKey[Option[Int]]("jmx-port", "If specified, launch forked JVM with JMX agent running on this port")
  val programArgs = SettingKey[Seq[String]]("program-args", "Arguments to pass to forked JVM process")

  // project build can assign this task to a key, typically (test in IntegrationTest)
  val itTestWithDependencies = taskKey[Unit]("run integration tests after starting dependencies")
}
