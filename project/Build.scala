import sbt._
import sbt.Keys._
import BackgroundService._
import BackgroundServiceKeys._

import sbtassembly.AssemblyPlugin.autoImport._

object SparkleBuild extends Build {
  import Dependencies._

  // set prompt to name of current project
  override lazy val settings = super.settings ++ Seq(
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
  )

  lazy val sparkleRoot = Project(id = "root", base = file("."))
    .aggregate(sparkleCore, sparkleDataServer, protocol, kafkaLoader, sparkShell, testKit, 
      sparkleTests, sparkleStore, storeTestKit, storeTests, sparkleLoader, sparkleAvro,
      util, utilTests, logbackConfig, log4jConfig, httpCommon, utilKafka,
      cassandraServer, zookeeperServer, kafkaServer
    )

  lazy val protocol =       // protocol server library serving the sparkle data api
    Project(id = "sparkle-protocol", base = file("protocol"))
      .dependsOn(util)
      .dependsOn(sparkleCore)
      .dependsOn(sparkleStore % "compile->compile") // compile->compile to avoid test libs too
      .dependsOn(httpCommon)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= logbackTest ++ Seq(
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
        libraryDependencies ++= logbackTest ++ Seq(
          scalaReflect,
          spire
        )
      )


  lazy val sparkleStore =
    Project(id = "sparkle-store", base = file("store"))
      .dependsOn(util)
      .dependsOn(sparkleCore)
      .dependsOn(testKit % "it->compile;test->compile") // testKit libs for our it, test
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        libraryDependencies ++= cassandraClient ++ logbackTest ++ Seq (
          sprayCaching,
          sprayJson,  // needed?
          sprayUtil,  // needed?
          openCsv
        ),
        dependenciesToStart := Seq(cassandraServer),
        test in IntegrationTest := BackgroundService.itTestTask.value
     )

  lazy val storeTestKit =        // utilities for testing sparkle stuff
    Project(id = "sparkle-store-test-kit", base = file("store-test-kit"))
      .dependsOn(util)
      .dependsOn(testKit % "compile->compile")
      .dependsOn(sparkleStore % "compile->compile")
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= storeKitTestsAndLogging ++ logbackTest
      )

  lazy val storeTests = 
    Project(id = "sparkle-store-tests", base = file("sparkle-store-tests"))
      .dependsOn(sparkleStore)
      .dependsOn(storeTestKit % "it->compile;test->compile")
      .dependsOn(logbackConfig)
      .dependsOn(util)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        libraryDependencies ++= logbackTest ++ Seq(
          metricsGraphite
        ),
        dependenciesToStart := Seq(cassandraServer),
        test in IntegrationTest := BackgroundService.itTestTask.value
      )

  lazy val sparkleLoader =
    Project(id = "sparkle-loader", base = file("loader"))
      .dependsOn(sparkleStore % "compile->compile")
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= logbackTest
      )

  lazy val sparkleAvro =
    Project(id = "sparkle-avro-loader", base = file("avro-loader"))
      .dependsOn(sparkleStore % "compile->compile")
      .dependsOn(sparkleLoader)
      .dependsOn(testKit % "it->compile;test->compile")
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= logbackTest ++ avro
      )

  lazy val kafkaLoader =    // loading from kafka into the store
    Project(id = "sparkle-kafka-loader", base = file("kafka"))
      .dependsOn(sparkleStore % "compile->compile") // we don't want store's logback in test,it
      .dependsOn(storeTestKit % "it->compile;test->compile")
      .dependsOn(sparkleLoader)
      .dependsOn(utilKafka % "compile->compile;test->test;it->it") // so we get it too
      .dependsOn(log4jConfig)
      .dependsOn(httpCommon)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        dependenciesToStart := Seq(kafkaServer, cassandraServer),
        test in IntegrationTest := BackgroundService.itTestTask.value
      )
      .settings(
        libraryDependencies ++= allTest
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
        libraryDependencies ++= 
          spray ++ kitTestsAndLogging ++ Seq(nScalaTime) ++ Seq(
            Runtime.logback % "test;it"
          )
      )

  lazy val protocolTestKit =        // utilities for testing sparkle protocol
    Project(id = "sparkle-protocol-test-kit", base = file("protocol-test-kit"))
      .dependsOn(protocol)
      .dependsOn(testKit % "compile->compile")
      .dependsOn(storeTestKit)
      .dependsOn(sparkleStore % "compile->compile")
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= logbackTest
      )

  lazy val sparkleTests =   // unit and integration for sparkle core and protocol libraries
    Project(id = "sparkle-tests", base = file("sparkle-tests"))
      .dependsOn(protocol)
      .dependsOn(protocolTestKit % "it->compile;test->compile")
      .dependsOn(testKit % "it->compile;test->compile")
      .dependsOn(logbackConfig)
      .dependsOn(util)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        libraryDependencies ++= logbackTest ++ spray ++ Seq(
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
        libraryDependencies ++= logbackTest ++ Seq(
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

  // tests for util, broken out to avoid circular dependency with sparkle-test-kit
  lazy val utilTests =          
    Project(id = "util-tests", base = file("util-tests"))
      .dependsOn(testKit % "it->compile;test->compile")
      .dependsOn(util)
      .dependsOn(logbackConfig)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= logbackTest ++ Seq(
          Test.scalaTest,
          Test.scalaCheck
        ) 
      )

  lazy val utilKafka =           // kafka utilities useful in other projects too
    Project(id = "sparkle-util-kafka", base = file("util-kafka"))
      .dependsOn(util % "compile->compile")
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        libraryDependencies ++= kafka ++ testAndLogging ++ log4jLogging ++ Seq(sprayJson),
        dependenciesToStart := Seq(kafkaServer),
        test in IntegrationTest := BackgroundService.itTestTask.value
      )

  lazy val httpCommon =           // http/spray common code
    Project(id = "sparkle-http", base = file("http-common"))
      .dependsOn(util)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= akka ++ spray ++ logbackTest 
      )

  lazy val logbackConfig =  // mix in to projects choosing logback
    Project(id = "logback-config", base = file("logback"))
      .dependsOn(util)
      .settings(BuildSettings.allSettings: _*)
      .settings(
        libraryDependencies ++= Seq(Runtime.logback) ++ Seq(Test.scalaTest)
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
      .dependsOn(sparkleStore)           // is "compile->compile" the default?
      .dependsOn(sparkleLoader)
      .dependsOn(storeTestKit % "it->compile;test->compile")  // is this just "it;test"?
      .dependsOn(logbackConfig)
      .settings(
        // http://stackoverflow.com/questions/25035716/how-to-exclude-transitive-dependencies-of-other-subproject-in-multiproject-build
        projectDependencies := {
          Seq(
            (projectID in sparkleLoader).value
              .exclude("nl.grons", "metrics-scala_2.10")
              .exclude("com.typesafe.akka", "akka-actor_2.10"),
            (projectID in sparkleStore).value
              .exclude("nl.grons", "metrics-scala_2.10")
              .exclude("com.typesafe.akka", "akka-actor_2.10"),
            (projectID in logbackConfig).value,
            (projectID in storeTestKit).value % "it;test"
          )
        },
        assemblyExcludedJars in assembly := { 
          val classpath = (fullClasspath in assembly).value
          classpath.filter{ attributedFile => 
            val name = attributedFile.data.getName
            name match {
              case _ if name.endsWith("-sources.jar")   => true // cassandra driver includes sources
              case _ if name.endsWith("minlog-1.2.jar") => true // probably better in current rev. see https://github.com/EsotericSoftware/kryo/issues/189
              case _                                    => false  
            }
          }
        }
      )
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BackgroundService.settings: _*)
      .settings(BuildSettings.setMainClass("org.apache.spark.repl.Main"): _*)
      .settings(
        libraryDependencies ++= spark ++ logbackTest ++ Seq(
          sparkRepl
        ),
        dependenciesToStart := Seq(cassandraServer),
        test in IntegrationTest := BackgroundService.itTestTask.value,
        // probably want to run the spark-repl here..
        initialCommands in console := """
          import nest.sparkle.shell.SparkConnection
          nest.sparkle.util.LogUtil.configureLogging(com.typesafe.config.ConfigFactory.load())
        """
      )

  // The following projects are for starting servers for integration tests
  lazy val sparkleDataServer =  // standalone protocol server
    Project(id = "sparkle-data-server", base = file("data-server"))
      .dependsOn(protocol)
      .dependsOn(protocolTestKit % "it->compile;test->compile")
      .dependsOn(logbackConfig)
      .configs(IntegrationTest)
      .settings(BuildSettings.allSettings: _*)
      .settings(BuildSettings.setMainClass("nest.sparkle.time.server.Main"): _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        healthPort := Some(1235),
        dependenciesToStart := Seq(cassandraServer),
        libraryDependencies ++= logbackTest, 
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
