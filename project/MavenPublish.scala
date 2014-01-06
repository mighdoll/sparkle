import sbt._
import sbt.Keys._
import MavenPublishTasks._
import MavenPublishKeys._
import sbt.Def.Initialize
import scala.util.control.Exception._

/**
 * MavenPublish is an sbt library (someday an sbt plugin) that allows sbt builds to
 * publish to a maven repository based on configuration files in the users home directory.
 *
 * This enables sbt builds to publish their artifacts a local workgroup repository 
 * as well as a public repository.  This allows workgroup developers to easily publish 
 * changes from forks and branches, and to avoid dependencies on a central repository.
 */

/** Keys that control the mavenPublish command.   Setting keys are available to
 *  override the maven repository url and the repository paths to snapshots and releases.
 */
object MavenPublishKeys {
  val maven = config("maven")

  val mavenRepository = settingKey[Option[String]]("url of the maven repository (defaults to the host property in ~/.maven.credentials)") in maven

  // LATER these settings should get defaults from the config file too
  val snapshotDirectory = settingKey[String]("path in the maven repo to snapshots") in maven
  val releaseDirectory = settingKey[String]("path in the maven repo to releases") in maven
}

/** Adds an sbt command 'mavenPublish' and associated settings.  mavenPublish will publish
 *  artifacts to a maven repository.  The maven repository url and credentials are configured
 *  by a file in the user's home directory:  ~/.maven/.credentials.  The paths in the
 *  repository to snapshot and release directories are configurable as sbt settings.
 *  
 *  mavenPublish is implemented as an sbt command (rather than an sbt task) so as not to conflict
 *  with other plugins that use the 'publish' command.
 */
object MavenPublish {
  // read the maven repo host,user,password off disk
  val mavenCredentials = Credentials(Path.userHome / ".maven" / ".credentials")
  val directCredentials: Option[DirectCredentials] = {
    catching(classOf[RuntimeException]) opt Credentials.toDirect(mavenCredentials)
  }
  val repoUrl: Option[String] = directCredentials map { "http://" + _.host + "/" }

  lazy val settings = Seq(
    mavenRepository := repoUrl,
    credentials += mavenCredentials,
    snapshotDirectory := "artifactory/libs-snapshot-local",
    releaseDirectory := "artifactory/libs-release-local",
    commands += publishCommand    
  )
}

object MavenPublishTasks {  
  /** a publish command modified to use the custom mavenResolver.  (The custom resolver
   *  is configured by the .credentials file, and separates snapshots and releases
   *  into separate directories in the maven repository).  */
  def publishCommand = Command.command("mavenPublish") { state =>
    val extracted = Project.extract(state)

    Project.runTask(
      publish in Compile,
      extracted.append(List(publishTo <<= mavenResolver), state),
      true
    )

    state
  }

  /** maven repositories separate snapshots from releases.  Return the appropriate repository
    * url based on the project version.
    */
  lazy val mavenResolver: Initialize[Option[Resolver]] = Def.setting {
    val releaseVersion = version.value
    mavenRepository.value.map { url: String =>  // temporary
      if (releaseVersion.trim.endsWith("SNAPSHOT"))
        "snapshots" at (url + releaseDirectory.value)
      else
        "releases" at (url + releaseDirectory.value)
    }
  }
}

