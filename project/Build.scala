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
    .aggregate(sparkleCore, sparkleDataServer, protocol, kafkaLoader, sparkShell, testKit, 
      sparkleTests, sparkleStore, sparkleLoader, sparkleAvro,
      util, logbackConfig, log4jConfig, httpCommon, utilKafka,
      cassandraServer, zookeeperServer, kafkaServer
    )

  lazy val protocol =       // protocol server library serving the sparkle data api
    Project(id = "sparkle-protocol", base = file("protocol"))
      .dependsOn(util)
      .dependsOn(sparkleCore)
      .dependsOn(sparkleStore)
      .dependsOn(httpCommon)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= kafka ++ testAndLogging ++ avro ++ Seq(
          nettyAll,
          unfiltered,
          metricsScala
        )
      )

  lazy val sparkleCore =      // core libraries for streaming data
    Project(id = "sparkle-core", base = file("sparkle"))
      .dependsOn(util)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        resolvers += "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases", // TODO - needed?
        libraryDependencies ++= Seq(
          scalaReflect,
          spire
        ),
//        adminPort := Some(18000), // enable when admin supports /shutdown
        healthPort := Some(18001)
      )


  lazy val sparkleStore =
    Project(id = "sparkle-store", base = file("store"))
      .dependsOn(util)
      .dependsOn(sparkleCore)
      .dependsOn(testKit % "it->compile")
      .dependsOn(log4jConfig % "it->compile")
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        libraryDependencies ++= cassandraClient ++ testAndLogging ++ Seq (
          sprayCaching,
          sprayJson,
          sprayUtil,
          openCsv
        ),
        dependenciesToStart := Seq(cassandraServer),
        test in IntegrationTest := BackgroundService.itTestTask.value
     )

  lazy val sparkleLoader =
    Project(id = "sparkle-loader", base = file("loader"))
      .dependsOn(sparkleStore)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= testAndLogging
      )

  lazy val sparkleAvro =
    Project(id = "sparkle-avro-loader", base = file("avro-loader"))
      .dependsOn(sparkleStore)
      .dependsOn(sparkleLoader)
      .dependsOn(testKit)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= kitTestsAndLogging ++ avro
      )

  lazy val kafkaLoader =    // loading from kafka into the store
    Project(id = "sparkle-kafka-loader", base = file("kafka"))
      .dependsOn(sparkleStore % "compile->compile;it->it;it->compile")
      .dependsOn(sparkleLoader)
      .dependsOn(utilKafka % "compile->compile;test->test;it->it")
      .dependsOn(log4jConfig)
      .dependsOn(httpCommon)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        dependenciesToStart := Seq(kafkaServer, cassandraServer),
        test in IntegrationTest := BackgroundService.itTestTask.value
      )

  lazy val sparkAvro =   // avro libraries for spark loading
    Project(id = "spark-avro", base = file("spark-avro"))
      .dependsOn(sparkShell)
      .dependsOn(sparkleAvro)
      .dependsOn(logbackConfig)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= testAndLogging ++ spark ++ avro ++ Seq(
          apacheAvroMapred
        )
      )

  lazy val testKit =        // utilities for testing sparkle stuff
    Project(id = "sparkle-test-kit", base = file("test-kit"))
      .dependsOn(util)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= spray ++ kitTestsAndLogging ++ Seq(nScalaTime)
      )

  lazy val protocolTestKit =        // utilities for testing sparkle protocol
    Project(id = "sparkle-protocol-test-kit", base = file("protocol-test-kit"))
      .dependsOn(protocol)
      .dependsOn(sparkleStore % "compile->compile;it->it;compile->it;it->compile")
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)

  lazy val sparkleTests =   // unit and integration for sparkle core and protocol libraries
    Project(id = "sparkle-tests", base = file("sparkle-tests"))
      .dependsOn(protocol)
      .dependsOn(protocolTestKit % "it->compile;test->compile")
      .dependsOn(testKit % "it->compile")
      .dependsOn(logbackConfig)
      .dependsOn(util)
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
          guava,
          spire,
          scalaLogging,
          metricsScala,
          scalaConfig,
          nScalaTime,
          rxScala,
          Optional.metricsGraphite,
          sprayJson,
          sprayCan % "optional",
          sprayRouting % "optional"
        ) ++ allTest
      )

  lazy val httpCommon =           // http/spray common code
    Project(id = "sparkle-http", base = file("http-common"))
      .dependsOn(util)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= akka ++ spray ++ testAndLogging ++ Seq(
          Runtime.logback % "test"
        )
      )

  lazy val utilKafka =           // kafka utilities useful in other projects too
    Project(id = "sparkle-util-kafka", base = file("util-kafka"))
      .dependsOn(util)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        libraryDependencies ++= kafka ++ testAndLogging ++ log4jLogging ++ Seq(sprayJson),
        dependenciesToStart := Seq(kafkaServer),
        test in IntegrationTest := BackgroundService.itTestTask.value
      )

  lazy val logbackConfig =  // mix in to projects choosing logback
    Project(id = "logback-config", base = file("logback"))
      .dependsOn(util)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= logbackLogging ++ Seq(Test.scalaTest)
      )

  lazy val log4jConfig =   // mix in to projects choosing log4j (kafka requires log4j)
    Project(id = "log4j-config", base = file("log4j"))
      .dependsOn(util)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= log4jLogging ++ Seq(Test.scalaTest)
      )

  lazy val sparkShell =   // admin shell
    Project(id = "spark-repl", base = file("spark"))
      .dependsOn(sparkleStore)
      .dependsOn(sparkleLoader)
      .dependsOn(protocolTestKit % "it->compile;test->compile")
      .dependsOn(logbackConfig)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        libraryDependencies ++= spark ++ testAndLogging ++ Seq(
          sparkRepl
        ),
        dependenciesToStart := Seq(cassandraServer),
        test in IntegrationTest := BackgroundService.itTestTask.value
      )

  // The following projects are for starting servers for integration tests

  lazy val sparkleDataServer =  // standalone protocol server
    Project(id = "sparkle-data-server", base = file("data-server"))
      .dependsOn(protocol)
      .dependsOn(protocolTestKit % "it->compile;test->compile")
      .dependsOn(logbackConfig)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BuildSettings.sparkleAssemblySettings: _*)
      .settings(BuildSettings.setMainClass("nest.sparkle.time.server.Main"): _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        dependenciesToStart := Seq(cassandraServer),
        initialCommands in console := """
          import nest.sg.Plot._
          import nest.sg.StorageConsole._
          import nest.sparkle.store.Event
          import rx.lang.scala.Observable
          import scala.concurrent.duration._
          """
      )

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
