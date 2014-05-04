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

import sbt._
import sbt.Keys._
import MavenPublishTasks._
import MavenPublishKeys._
import sbt.Def.Initialize
import scala.util.control.Exception._
import scala.collection.JavaConverters._
import java.io.BufferedReader
import java.io.FileInputStream
import java.util.Properties
import scala.language.postfixOps

/** MavenPublish is an sbt library (someday an sbt plugin) that allows sbt builds to
  * publish to a maven repository based on configuration files in the users home directory.
  * MavenPublish adds an sbt command 'mavenPublish' to any project that imports it.
  *
  * MavenPublish enables sbt builds to publish their artifacts a local workgroup repository
  * as well as a public repository.  Workgroup developers can easily publish
  * changes from forks and branches, while avoiding dependencies on a central repository.
  */

/** Keys that control the mavenPublish command.   Setting keys are available to
  * override the maven repository url and the repository paths to snapshots and releases.
  */
object MavenPublishKeys {
  val mavenRepository = settingKey[Option[String]]("url of the maven repository (defaults to the host property in ~/.maven/.credentials)")

  val mavenSnapshotDirectory = settingKey[Option[String]]("path in the maven repo to snapshots")
  val mavenReleaseDirectory = settingKey[Option[String]]("path in the maven repo to releases")
}

// format: OFF
/** Adds an sbt command 'mavenPublish' and associated settings.  mavenPublish will publish
  * artifacts to a maven repository.  The maven repository url and credentials are configured
  * by a file in the user's home directory:  ~/.maven/.credentials.  The paths in the
  * repository to snapshot and release directories are configurable as sbt settings or in the
  * .credentials file.
  *
  * The .credentials file looks like this:
  *
  * <code>
    $ cat ~/.maven/.credentials
        realm=Artifactory Realm
        host=artifactory.my-workgroup-domain.com
        user=my-user-name
        password=my-password
        snapshotsPath=artifactory/libs-snapshot-local
        releasesPath=artifactory/libs-release-local
    </code>
  *
  * mavenPublish is implemented as an sbt command (rather than an sbt task) so as not to conflict
  * with other plugins that use the 'publish' command.
  */
object MavenPublish { // format: ON
  /** users should normally include these settings in their sbt project */
  lazy val settings = Seq(
    credentials += mavenCredentials,
    mavenRepository := repositoryPaths.map { _.repoUrl },
    mavenReleaseDirectory := repositoryPaths.map { _.releases },
    mavenSnapshotDirectory := repositoryPaths.map { _.snapshots },
    commands += publishCommand
  )
}

object MavenPublishTasks {
  /** a publish command modified to use the custom mavenResolver.  (The custom resolver
    * is configured by the .credentials file, and separates snapshots and releases
    * into separate directories in the maven repository).
    */
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
    mavenRepository.value.map { url: String => // temporary
      if (releaseVersion.trim.endsWith("SNAPSHOT"))
        "snapshots" at (url + mavenSnapshotDirectory.value.getOrElse(""))
      else
        "releases" at (url + mavenReleaseDirectory.value.getOrElse(""))
    }
  }

  case class RepositoryPaths(repoUrl: String, releases: String, snapshots: String)

  lazy val (mavenCredentials, repositoryPaths) =
    credentialsFromEnvironment match {
      case Some(credentials) => (credentials, repositoryPathsFromEnv)
      case None => (credentialsFromFile, repositoryPathsFromFile)
    }

  lazy val valuesFromEnvironment =
    for {   // Must have all four defined 
      realm     <- sys.env.get("MAVEN_PUBLISH_REALM")
      host      <- sys.env.get("MAVEN_PUBLISH_HOST")
      user      <- sys.env.get("MAVEN_PUBLISH_USER")
      pswd      <- sys.env.get("MAVEN_PUBLISH_PASSWORD")
    } yield (realm, host, user, pswd)

  private def credentialsFromEnvironment =
    for {
      (realm, host, user, pswd) <- valuesFromEnvironment
    } yield Credentials(realm, host, user, pswd)

  private def repositoryPathsFromEnvironment: Option[RepositoryPaths] =
    for {   // Must have all four defined to match source of credentials
      (realm, host, user, pswd) <- valuesFromEnvironment
    } yield {
      val releases  = sys.env.get("MAVEN_PUBLISH_RELEASES_PATH").getOrElse("artifactory/libs-release-local")
      val snapshots = sys.env.get("MAVEN_PUBLISH_SNAPSHOTS_PATH").getOrElse("artifactory/libs-snapshot-local")
      RepositoryPaths(s"http://$host/", releases, snapshots)
    }

  private lazy val credentialsPath = Path.userHome / ".maven" / ".credentials"

  private def credentialsFromFile = Credentials(credentialsPath)

  private def repositoryPathsFromFile: Option[RepositoryPaths] =
      if (credentialsPath.exists()) {
        val properties = readProperties(credentialsPath)
        val repoUrl = "http://" + properties("host") + "/"
        val releases = properties.get("releasesPath").getOrElse("artifactory/libs-release-local")
        val snapshots = properties.get("snapshotsPath").getOrElse("artifactory/libs-snapshot-local")
        Some(RepositoryPaths(repoUrl, releases, snapshots))
      } else {
        None
      }

  /** read a java properties file */
  private def readProperties(file: File): Map[String, String] = {
    val properties = new Properties()
    val fileInput = new FileInputStream(file)
    try {
      properties.load(fileInput)
      properties.asScala.map { case (k, v) => (k.toString, v.toString.trim) } toMap
    } finally {
      fileInput.close()
    }
  }
}

