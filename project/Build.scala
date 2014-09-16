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

object sparkleCoreBuild extends Build {
  import Dependencies._

  // set prompt to name of current project
  override lazy val settings = super.settings :+ {
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
  }

  lazy val sparkleRoot = Project(id = "root", base = file("."))
    .aggregate(sparkleCore, sparkleDataServer, protocol, kafkaLoader, sparkShell, testKit, sparkleTests, util, logbackConfig, log4jConfig)

  lazy val sparkleDataServer =  // standalone protocol server
    Project(id = "sparkle-data-server", base = file("data-server"))
      .dependsOn(protocol)
      .dependsOn(logbackConfig)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BuildSettings.sparkleAssemblySettings: _*) 
      .settings(BuildSettings.setMainClass("nest.sparkle.time.server.Main"): _*)


  lazy val protocol =       // protocol server library serving the sparkle data api
    Project(id = "sparkle-protocol", base = file("protocol"))
      .dependsOn(sparkleCore)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= kafka ++ testAndLogging ++ avro ++ Seq(
          metricsScala
        )
      )

  lazy val sparkleCore =      // core libraries shared by protocol server and stream loader
    Project(id = "sparkle-core", base = file("sparkle"))
      .dependsOn(util)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        resolvers += "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases", // TODO - needed?
        libraryDependencies ++= cassandraClient ++ akka ++ spray ++ testAndLogging ++ Seq(
        libraryDependencies ++= cassandraClient ++ akka ++ spray ++ testAndLogging ++ spark ++ Seq(
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

  lazy val kafkaLoader =    // loading from kafka into the store
    Project(id = "sparkle-kafka-loader", base = file("kafka"))
      .dependsOn(sparkleCore)
      .dependsOn(testKit)
      .dependsOn(log4jConfig)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= kafka ++ testAndLogging ++ avro ++ Seq(
          metricsScala
        )
      )

  lazy val sparkShell =   // admin shell
    Project(id = "spark-repl", base = file("spark"))
      .dependsOn(sparkleCore)
      .dependsOn(testKit)
      .dependsOn(logbackConfig)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= testAndLogging ++ Seq(
          sparkRepl
        )
      )

  lazy val testKit =        // utilities for testing sparkle stuff
    Project(id = "sparkle-test-kit", base = file("test-kit"))
      .dependsOn(sparkleCore)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= kitTestsAndLogging ++ spray
      )

  lazy val sparkleTests =   // unit and integration for sparkle core and protocol libraries
    Project(id = "sparkle-tests", base = file("sparkle-tests"))
      .dependsOn(protocol)
      .dependsOn(testKit)
      .dependsOn(logbackConfig)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= testAndLogging ++ spray ++ Seq(
          metricsGraphite
        )
      )

  lazy val util =           // scala utilities useful in other projects too
    Project(id = "sparkle-util", base = file("util"))
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= Seq(
          argot,
          scalaLogging,
          metricsScala,
          scalaConfig,
          Optional.metricsGraphite,
          sprayCan     % "optional",
          sprayRouting % "optional"
          ) ++ allTest
      )

  lazy val logbackConfig =  // mix in to projects choosing logback
    Project(id = "logback-config", base = file("logback"))
      .dependsOn(util)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= Seq(
          scalaConfig
        ) ++ logbackLogging
      )

  lazy val log4jConfig =   // mix in to projects choosing log4j (kafka requires log4j)
    Project(id = "log4j-config", base = file("log4j"))
      .dependsOn(util)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= Seq(
          scalaConfig
        ) ++ log4jLogging
      )

}
