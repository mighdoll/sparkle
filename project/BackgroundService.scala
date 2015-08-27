import sbt._
import sbt.Keys._
import sbt.Def.Initialize
import scala.util.Try
import BackgroundServiceKeys._
import spray.revolver.RevolverPlugin.Revolver
import spray.revolver.AppProcess
import scala.util.{ Success, Failure }
import scala.concurrent.duration._
import java.util.concurrent.TimeoutException
import Utils.retryBlocking

object BackgroundService {

  /** Builds should normally import these default settings */
  val settings = Revolver.settings ++ Seq(
    dependenciesToStart := Seq(),
    startDependencies := startDependenciesTask.value,
    stopDependencies := stopDependenciesTask.value,
    start := startServiceTask.value,
    stop := stopServiceTask.value, 
    startSolo := startSoloTask.value,
    stopSolo := stopSoloTask.value,
    status := Revolver.reStatus.value,
    healthCheckFn := { () => Success() }, // TODO instead check the health port by default
    healthCheck := healthCheckTask.value,
    waitUntilHealthy := waitUntilHealthyTask.value,
    Revolver.reLogTag := name.value,
    jmxPort := None,
    programArgs := Nil,
    adminPort := None,
    healthPort := None
  //    (test in IntegrationTest) := itTestTask.value  // LATEr enable this by conditionally checking wither IntegrationTest configuration is present
  )

  /** a replacement for it:test that first launches the dependecy services. */
  lazy val itTestTask: Initialize[Task[Unit]] = Def.taskDyn {
    // to avoid initialization issues (using the same key we're replacing), we repeat what it:test does 
    // in sbt and call executeTests with appropriate logging
    // (SCALA/SBT is there a better way?)
    val resultLogger = (testResultLogger in (Test, test)).value
    val taskName = Project.showContextKey(state.value)(resolvedScoped.value)
    val log = streams.value.log

    (executeTests in IntegrationTest).map { results =>
      resultLogger.run(log, results, taskName)
    }.dependsOn(startDependencies)
  }


  /** task that returns true/false if the service is healthy, based on the Build provided healthCheckFn */
  private lazy val healthCheckTask: Initialize[Task[Boolean]] = Def.task {
    val logger = spray.revolver.Utilities.colorLogger(streams.value.log)
    val healthy = healthCheckFn.value.apply().isSuccess

    val healthyStr = if (healthy) { "[GREEN]healthy" } else { "[RED]not healthy" }
    logger.info(s"${name.value} is $healthyStr")

    healthy
  }

  /** start services that this service depends on */
  private lazy val startDependenciesTask: Initialize[Task[Unit]] = Def.taskDyn {
    val starts = dependenciesToStart.value.map { project => (start in project) }
    Def.task().dependsOn(starts: _*)
  }

  /** stop services that this service depends on */
  private lazy val stopDependenciesTask: Initialize[Task[Unit]] = Def.taskDyn {
    val stops = dependenciesToStart.value.map { project => (stop in project) }
    Def.task().dependsOn(stops: _*)
  }
  
  /** start dependent services, then start this service */
  private lazy val startServiceTask: Initialize[Task[Unit]] = Def.taskDyn {
    startSolo.dependsOn(startDependencies)
  }

  /** stop this service, then stop dependent services */
  private lazy val stopServiceTask: Initialize[Task[Unit]] = Def.taskDyn {
    stopDependencies.dependsOn(stopSolo)
  }
  
  /** stop this service, via the admin port if it has one, otherwise by terminating the jvm */
  private lazy val stopSoloTask: Initialize[Task[Unit]] = Def.taskDyn {
    adminPort.value.foreach { port =>
      RunServices.stopService(streams.value, name.value, port)
      awaitShutdown.value
    }

    Revolver.reStop // kills the jvm
  }
  
  /** Returns an sbt task which starts a invokes the revolver start mechanism
    * and then blocks until the service is healthy.
    *
    * We invoke revolver directly so that we have control over the parameters passed into revolver
    */
  private lazy val startSoloTask: Initialize[Task[Unit]] = Def.taskDyn {
    val _ = (products in Compile).value // TODO necessary?

    val logger = spray.revolver.Utilities.colorLogger(streams.value.log)
    lazy val isHealthy: Boolean = healthCheckFn.value.apply().isSuccess 

    spray.revolver.Actions.revolverState.getProcess(thisProjectRef.value) match {
      case Some(process) if process.isRunning =>
        logger.info(s"${Revolver.reLogTag.value} already running")
        emptyTask
      case _ if isHealthy =>
        logger.info(s"${Revolver.reLogTag.value} already healthy")
        emptyTask
      case _ =>
        waitUntilHealthy.dependsOn(startWithRevolver) // TODO use SbtUtil.inOrder?
    }
  }

  /** an Sbt task that blocks until this service's health check succeeds */
  private lazy val waitUntilHealthyTask = Def.task {
    val logger = spray.revolver.Utilities.colorLogger(streams.value.log)

    val tryHealth: () => Try[Unit] = healthCheckFn.value

    /** Return a try with the result of the healthCheckFn.
     *   
     *  If the background process has died, throw an exception, aborting further processing 
     *  of this command.
     */
    def checkHealth(): Try[Unit] = {
      tryHealth() match {
        case checked: Success[Unit] =>
          checked
        case checkFail: Failure[Unit] if processRunning() =>
          checkFail // could be still trying to get started
        case Failure(err) =>
          logger.info(s"[RED]application ${name.value} has died; abort")
          throw err
      }
    }

    /** return true if revolver knows about the service and reports it as running */
    def processRunning(): Boolean = {
      spray.revolver.Actions.revolverState.getProcess(thisProjectRef.value) match {
        case Some(process) if process.isRunning => true
        case None                               => false
      }
    }

    val projectName = name.value
    logger.info(s"[YELLOW]waiting for application $projectName to be healthy")
    
    retryBlocking(30.seconds, 250.millis) { checkHealth() }
    
    logger.info(s"[GREEN]application $projectName is healthy!")
  }

  /** A task that launches a service in a background process with Revolver.
    * Returns once the project process has been forked (but probably before the app
    * has initialized itself).
    */
  private lazy val startWithRevolver = Def.task {

    val logger = spray.revolver.Utilities.colorLogger(streams.value.log)

    val jmxArgs = jmxPort.value.toSeq.flatMap { port =>
      Seq(
        "-Djava.rmi.server.hostname=localhost",
        s"-Dcom.sun.management.jmxremote.port=$port",
        "-Dcom.sun.management.jmxremote.ssl=false",
        "-Dcom.sun.management.jmxremote.authenticate=false")
    }

    val jvmOptions = javaOptions.value

    // PROJECT_HOME defaults to value of system property
    val projectHome = if (!jvmOptions.exists(_.startsWith("-DPROJECT_HOME="))) {
      Seq(s"-DPROJECT_HOME=${baseDirectory.value.getAbsolutePath}")
    } else { Nil }

    val jvmArgs = jmxArgs ++ projectHome ++ jvmOptions

    val extraOptions = spray.revolver.Actions.ExtraCmdLineOptions(jvmArgs = jvmArgs, startArgs = programArgs.value)

    val main = (mainClass in Revolver.reStart).value
    logger.info(
      s"[BLUE]about to run ${Revolver.reLogTag.value} (${main.get}) "
        + s"with command line arguments ${extraOptions.startArgs} and JVM arguments $jvmArgs"
    )

    spray.revolver.Actions.startApp(
      streams = streams.value,
      logTag = Revolver.reLogTag.value,
      project = thisProjectRef.value,
      options = Revolver.reForkOptions.value,
      mainClass = main,
      cp = (fullClasspath in sbt.Runtime).value,
      args = Revolver.reStartArgs.value,
      startConfig = extraOptions)

  }

  /** Block until there is no longer a Revolver process running  */
  private lazy val awaitShutdown = Def.task {
    retryBlocking(5.seconds, 100.millis) {
      spray.revolver.Actions.revolverState.getProcess(thisProjectRef.value) match {
        case Some(process) if !process.isRunning => Success()
        case None                                => Success()
        case _                                   => Failure(new TimeoutException)
      }
    }
  }
 
  /** Trivial do nothing task */
  private lazy val emptyTask = Def.task {}


}
