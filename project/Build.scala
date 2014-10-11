import sbt._
import sbt.Keys._
import BackgroundService._
import BackgroundServiceKeys._

object SparkleBuild extends Build {
  import Dependencies._

  // set prompt to name of current project
  override lazy val settings = super.settings ++ Seq(
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
  )

  lazy val sparkleRoot = Project(id = "root", base = file("."))
    .aggregate(sparkleCore, sparkleDataServer, protocol, kafkaLoader, sparkShell, testKit, sparkleTests, util, logbackConfig, log4jConfig,
      cassandraServer, zookeeperServer, kafkaServer
    )

  lazy val sparkleDataServer =  // standalone protocol server
    Project(id = "sparkle-data-server", base = file("data-server"))
      .dependsOn(protocol)
      .dependsOn(logbackConfig)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BuildSettings.sparkleAssemblySettings: _*)
      .settings(BuildSettings.setMainClass("nest.sparkle.time.server.Main"): _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        dependenciesToStart := Seq(cassandraServer)
      )


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
//        adminPort := Some(18000), // enable when admin supports /shutdown
        healthPort := Some(18001), 
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
      .settings(BackgroundService.settings: _*)
      .settings(
        libraryDependencies ++= kafka ++ testAndLogging ++ avro ++ Seq(
          metricsScala
        ),
        dependenciesToStart := Seq(kafkaServer, cassandraServer),
        test in IntegrationTest := BackgroundService.itTestTask.value
      )

  lazy val sparkShell =   // admin shell
    Project(id = "spark-repl", base = file("spark"))
      .dependsOn(sparkleCore)
      .dependsOn(testKit)
      .dependsOn(logbackConfig)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        libraryDependencies ++= testAndLogging ++ Seq(
          sparkRepl
        ),
        dependenciesToStart := Seq(cassandraServer),
        test in IntegrationTest := BackgroundService.itTestTask.value
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
      .settings(BackgroundService.settings: _*)
      .settings(
        libraryDependencies ++= testAndLogging ++ spray ++ Seq(
          metricsGraphite
        ),
        dependenciesToStart := Seq(cassandraServer),
        test in IntegrationTest := BackgroundService.itTestTask.value
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
          sprayCan % "optional",
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

  // The following projects are for starting servers for integration tests

  lazy val cassandraServer =
    Project(id = "cassandra-server", base = file("servers/cassandra"))
      .settings(BackgroundService.settings: _*)
      .settings(BuildSettings.setMainClass("org.apache.cassandra.service.CassandraDaemon"): _*)
      .settings(
        libraryDependencies ++= Seq(cassandraAll, lz4, snappy),
        jmxPort := Some(7199),
        healthCheckFn := HealthChecks.cassandraIsHealthy.value
      )

  lazy val kafkaServer =
    Project(id = "kafka-server", base = file("servers/kafka"))
      .settings(BackgroundService.settings: _*)
      .settings(BuildSettings.setMainClass("com.nestlabs.kafka.Main"): _*)
      .settings(
        libraryDependencies ++= Seq(
          apacheKafka,
          Runtime.slf4jlog4j
        ),
        healthCheckFn := HealthChecks.kafkaIsHealthy.value,
        dependenciesToStart := Seq(zookeeperServer)
      )

  lazy val zookeeperServer =
    Project(id = "zookeeper-server", base = file("servers/zookeeper"))
      .settings(BackgroundService.settings: _*)
      .settings(BuildSettings.setMainClass("com.nestlabs.zookeeper.Main"): _*)
      .settings(
        libraryDependencies ++= Seq(zookeeper),
        healthCheckFn := HealthChecks.zookeeperIsHealthy.value
      )

}
