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

  lazy val sparkleRoot = Project(id = "root", base = file("."))
    .aggregate(sparkleTime, kafkaLoader, testKit, sparkleTests)

  lazy val sparkleTime =
    Project(id = "sparkle", base = file("sparkle"))
      .dependsOn(util)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BuildSettings.sparkleAssemblySettings: _*) 
      .settings(BuildSettings.setMainClass("nest.sparkle.time.server.Main"): _*)
      .settings(
        resolvers += "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases",
        libraryDependencies ++= cassandraClient ++ akka ++ spray ++ testAndLogging ++ Seq(
          scalaReflect,
          rxJavaCore,
          rxJavaScala,
          spire,
          nScalaTime,
          argot,
          unfiltered,
          openCsv,
          metricsScala
        ),
        initialCommands in console := """
          import nest.sg.Plot._
          import nest.sg.StorageConsole._
          import nest.sparkle.store.Event
          import rx.lang.scala.Observable
          import scala.concurrent.duration._
          """
      )

  lazy val kafkaLoader =
    Project(id = "sparkle-kafka-loader", base = file("kafka"))
      .dependsOn(util)
      .dependsOn(sparkleTime)
      .dependsOn(testKit)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= kafka ++ testAndLogging ++ avro ++ Seq(
          metricsScala
        )
      )

  lazy val testKit =
    Project(id = "sparkle-test-kit", base = file("test-kit"))
      .dependsOn(sparkleTime)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= kitTestsAndLogging ++ spray
      )

  lazy val sparkleTests =
    Project(id = "sparkle-tests", base = file("sparkle-tests"))
      .dependsOn(sparkleTime)
      .dependsOn(testKit)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= testAndLogging ++ spray
      )

  lazy val util =
    Project(id = "sparkle-util", base = file("util"))
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= Seq(
          argot,
          scalaLogging,
          metricsScala,
          scalaConfig,
          Optional.logback,
          Optional.log4j,
          Optional.metricsGraphite,
          sprayCan % "optional",
          sprayRouting % "optional"
          )
      )

}
