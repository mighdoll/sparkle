/* Copyright 2013  Nest Labs

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

object SparkleTimeBuild extends Build {
  import Dependencies._
  // set prompt to name of current project
  override lazy val settings = super.settings :+ {
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
  }

  lazy val sparkleRoot = Project(id = "sparkle-root", base = file("."))
    .aggregate(sparkleTime, kafkaLoader)

  lazy val sparkleTime =
    Project(id = "sparkle-time", base = file("sparkle"))
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BuildSettings.setMainClass("nest.sparkle.time.server.Main"): _*)
      .settings(
        libraryDependencies ++= cassandraClient ++ akka ++ spray ++ testAndLogging ++ Seq(
          scalaReflect,
          rxJavaCore,
          rxJavaScala,
          spire,
          nScalaTime,
          argot,
          openCsv
        ),
        initialCommands in console := """import nest.sg.Plot._"""
      )

  lazy val kafkaLoader =
    Project(id = "sparkle-kafka-loader", base = file("kafka"))
      .dependsOn(sparkleTime)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= kafka ++ testAndLogging ++ avro
      ).dependsOn(sparkleTime)

}
