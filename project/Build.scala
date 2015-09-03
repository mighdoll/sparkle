import sbt._
import sbt.Keys._
import BackgroundServiceKeys._
import BuildSettings._
import com.typesafe.sbt.SbtSite.site

import sbtassembly.AssemblyPlugin.autoImport._

object SparkleBuild extends Build {
  import Dependencies._

  // set prompt to name of current project
  override lazy val settings = super.settings ++ Seq(
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
  )

  lazy val sparkleRoot = Project(id = "root", base = file("."))
    .aggregate(sparkleCore, sparkleDataServer, protocol, kafkaLoader, sparkShell, testKit, 
      sparkleTests, sparkleStore, storeTestKit, storeTests, sparkAvro, sparkleLoader, sparkleAvro,
      util, utilTests, logbackConfig, log4jConfig, httpCommon, utilKafka,
      cassandraServer, zookeeperServer, kafkaServer, doc
    )

  lazy val doc =       // protocol server library serving the sparkle data api
    Project(id = "doc", base = file("doc"))
    .settings(
      site.settings,
      site.jekyllSupport()
    ) 

  lazy val protocol =       // protocol server library serving the sparkle data api
    Project(id = "sparkle-protocol", base = file("protocol"))
      .dependsOn(util)
      .dependsOn(sparkleCore)
      .dependsOn(sparkleStore % "compile->compile") // compile->compile to avoid test libs too
      .dependsOn(httpCommon)
      .settings(sparkleSettings: _*)
      .settings(
        libraryDependencies ++= logbackUnitTest ++ web ++ Seq(
          unfiltered,
          metricsScala
        )
      )

  lazy val sparkleCore =      // core libraries for streaming data
    Project(id = "sparkle-core", base = file("sparkle"))
      .dependsOn(util)
      .settings(sparkleSettings: _*)
      .settings(
        resolvers += "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases", // TODO - needed?
        libraryDependencies ++= logbackUnitTest ++ Seq(
          spire
        ),
        libraryDependencies <+= scalaVersion(scalaReflect % _)  // TODO use += .value style
      )


  lazy val sparkleStore =
    Project(id = "sparkle-store", base = file("store"))
      .dependsOn(util)
      .dependsOn(sparkleCore)
      .dependsOn(testKit % "test") // testKit libs for our it, test
      .settings(sparkleSettings: _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        libraryDependencies ++= cassandraClient ++ logbackUnitTest ++ Seq (
          akkaActor,
          sprayCaching,
          sprayJson,  // needed?
          sprayUtil,  // needed?
          openCsv
        ),
        dependenciesToStart := Seq(cassandraServer)
     )

  lazy val storeTestKit =        // utilities for testing sparkle stuff
    Project(id = "sparkle-store-test-kit", base = file("store-test-kit"))
      .dependsOn(util)
      .dependsOn(testKit)
      .dependsOn(sparkleStore)
      .settings(sparkleSettings: _*)
      .settings(
        libraryDependencies ++= kitTests ++ logging ++ logbackUnitTest
      )

  lazy val storeTests = 
    Project(id = "sparkle-store-tests", base = file("sparkle-store-tests"))
      .dependsOn(sparkleStore)
      .dependsOn(storeTestKit % "it;test")
      .dependsOn(logbackConfig)
      .dependsOn(util)
      .configs(IntegrationTest)
      .settings(sparkleSettings ++ sparkleItSettings: _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        libraryDependencies ++= logbackIntegrationTest ++ Seq(
          metricsGraphite
        ),
        dependenciesToStart := Seq(cassandraServer),
        test in IntegrationTest := BackgroundService.itTestTask.value
      )

  lazy val sparkleLoader =
    Project(id = "sparkle-loader", base = file("loader"))
      .dependsOn(sparkleStore)
      .settings(sparkleSettings: _*)
      .settings(
        libraryDependencies ++= logbackUnitTest
      )

  lazy val sparkleAvro =
    Project(id = "sparkle-avro-loader", base = file("avro-loader"))
      .dependsOn(sparkleStore)
      .dependsOn(sparkleLoader)
      .dependsOn(testKit % "test;it")
      .configs(IntegrationTest)
      .settings(sparkleSettings ++ sparkleItSettings: _*)
      .settings(
        libraryDependencies ++= logbackIntegrationTest ++ avro
      )

  lazy val kafkaLoader =    // loading from kafka into the store
    Project(id = "sparkle-kafka-loader", base = file("kafka"))
      .configs(IntegrationTest)
      .dependsOn(sparkleStore)
      .dependsOn(storeTestKit % "it;test")
      .dependsOn(sparkleLoader)
      .dependsOn(utilKafka % "compile->compile;test->test;it->it") // so we get it too
      .dependsOn(log4jConfig)
      .dependsOn(httpCommon)
      .settings(sparkleSettings ++ sparkleItSettings ++ BackgroundService.settings : _*)
      .settings(
        dependenciesToStart := Seq(kafkaServer, cassandraServer),
        test in IntegrationTest := BackgroundService.itTestTask.value,
        libraryDependencies ++= unitTest ++ integrationTest
      )

  lazy val sparkAvro =   // avro libraries for spark loading
    Project(id = "spark-avro", base = file("spark-avro"))
      .dependsOn(sparkShell)
      .dependsOn(sparkleAvro)
      .dependsOn(logbackConfig)
      .settings(sparkleSettings: _*)
      .settings(
        libraryDependencies ++= logging ++ spark ++ avro ++ Seq(
          apacheAvroMapred
        )
      )

  lazy val testKit =        // utilities for testing sparkle stuff
    Project(id = "sparkle-test-kit", base = file("test-kit"))
      .dependsOn(util)
      .settings(sparkleSettings: _*)
      .settings(
        libraryDependencies ++= spray ++ kitTests ++ logging ++ Seq(nScalaTime)
      )

  lazy val protocolTestKit =        // utilities for testing sparkle protocol
    Project(id = "sparkle-protocol-test-kit", base = file("protocol-test-kit"))
      .dependsOn(protocol)
      .dependsOn(testKit)
      .dependsOn(storeTestKit)
      .dependsOn(sparkleStore)
      .settings(sparkleSettings: _*)
      .settings(
        libraryDependencies ++= logbackUnitTest
      )

  lazy val sparkleTests =   // unit and integration for sparkle core and protocol libraries
    Project(id = "sparkle-tests", base = file("sparkle-tests"))
      .configs(IntegrationTest)
      .dependsOn(protocol)
      .dependsOn(protocolTestKit % "it;test")
      .dependsOn(testKit % "it;test")
      .dependsOn(logbackConfig)
      .dependsOn(storeTestKit % "it;test")
      .dependsOn(util)
      .settings(sparkleSettings ++ sparkleItSettings : _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        libraryDependencies ++= logbackIntegrationTest ++ spray ++ sprayIntegrationTest ++ Seq(
          metricsGraphite,
          IT.tubeSocks
        ),
        dependenciesToStart := Seq(cassandraServer),
        test in IntegrationTest := BackgroundService.itTestTask.value
      )

  lazy val util =           // scala utilities useful in other projects too
    Project(id = "sparkle-util", base = file("util"))
      .settings(sparkleSettings: _*)
      .settings(
        libraryDependencies ++= logbackUnitTest ++ Seq(
          argot,
          guava,
          jsr,
          spire,
          scalaLogging,
          metricsScala,
          scalaConfig,
          nScalaTime,
          rxScala,
          Optional.metricsGraphite,
          akkaActor,
          sprayJson,
          sprayCan % "optional",
          sprayRouting % "optional"
        )
      )

  // tests for util, broken out to avoid circular dependency with sparkle-test-kit
  lazy val utilTests =          
    Project(id = "util-tests", base = file("util-tests"))
      .dependsOn(testKit % "test")
      .dependsOn(util)
      .dependsOn(logbackConfig)
      .settings(sparkleSettings: _*)
      .settings(
        libraryDependencies ++= logbackUnitTest ++ Seq(
          Test.scalaTest,
          Test.scalaCheck
        ) 
      )

  lazy val utilKafka =           // kafka utilities useful in other projects too
    Project(id = "sparkle-util-kafka", base = file("util-kafka"))
      .dependsOn(util)
      .configs(IntegrationTest)
      .settings(sparkleSettings ++ sparkleItSettings : _*)
      .settings(BackgroundService.settings: _*)
      .settings(
        updateOptions := updateOptions.value.withCachedResolution(false), // sbt bug, TODO file bug
        libraryDependencies ++= kafka ++ integrationTest ++ logging ++ log4jLogging ++ Seq(sprayJson),
        dependenciesToStart := Seq(kafkaServer),
        test in IntegrationTest := BackgroundService.itTestTask.value
      )

  lazy val httpCommon =           // http/spray common code
    Project(id = "sparkle-http", base = file("http-common"))
      .dependsOn(util)
      .dependsOn(testKit % "test")
      .dependsOn(logbackConfig % "test")
      .settings(sparkleSettings: _*)
      .settings(
        libraryDependencies ++= akka ++ spray ++ sprayTest ++ logbackUnitTest
      )

  lazy val logbackConfig =  // mix in to projects choosing logback
    Project(id = "logback-config", base = file("logback"))
      .dependsOn(util)
      .settings(sparkleSettings: _*)
      .settings(
        libraryDependencies ++= Seq(Runtime.logback) ++ Seq(Test.scalaTest)
      )

  lazy val log4jConfig =   // mix in to projects choosing log4j (kafka requires log4j)
    Project(id = "log4j-config", base = file("log4j"))
      .dependsOn(util)
      .settings(sparkleSettings: _*)
      .settings(
        libraryDependencies ++= log4jLogging ++ Seq(Test.scalaTest)
      )

  lazy val assemblyExclusions = 
    assemblyExcludedJars in assembly := {
      val classpath = (fullClasspath in assembly).value
      classpath.filter{ attributedFile =>
        val name = attributedFile.data.getName
        name match {
          case _ if name.endsWith("-sources.jar")                           => true // cassandra driver includes sources
          case _ if name.matches(raw".*?\bakka-actor_2\.\d+-2\.3\.11\.jar") => true // exclusion trick doesn't work with cached resolution
          case _ if name.endsWith("minlog-1.2.jar")                         => true // probably better in current rev. see https://github.com/EsotericSoftware/kryo/issues/189
          case _                                                            => false
        }
      }
    }

  lazy val sparkShell =   // admin shell
    Project(id = "spark-repl", base = file("spark"))
      .dependsOn(sparkleStore)           // is "compile->compile" the default?
      .dependsOn(sparkleLoader)
      .dependsOn(storeTestKit % "it->compile;test->compile")  // is this just "it;test"?
      .dependsOn(logbackConfig)
      .settings(assemblyExclusions)
      .configs(IntegrationTest)
      .settings(sparkleSettings ++ sparkleItSettings : _*)
      .settings(BackgroundService.settings: _*)
      .settings(BuildSettings.sparkMergeSettings: _*)
      .settings(BuildSettings.setMainClass("org.apache.spark.repl.Main"): _*)
      .settings(
        libraryDependencies ++= spark ++ logbackIntegrationTest ++ Seq(
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
      .configs(IntegrationTest)
      .dependsOn(protocol)
      .dependsOn(protocolTestKit % "it;test")
      .dependsOn(logbackConfig)
      .dependsOn(sparkShell)
      .settings(sparkleSettings ++ sparkleItSettings ++ dataServerMergeSettings
                ++ BackgroundService.settings ++ BuildSettings.sparkMergeSettings
                ++ BuildSettings.setMainClass("nest.sparkle.time.server.Main") : _*)
      .settings(
        healthPort := Some(1235),
        dependenciesToStart := Seq(cassandraServer),
        test in IntegrationTest := BackgroundService.itTestTask.value,

          //
          // The problem here is that the netty folks publish both netty-all and the netty 
          // component libraries separately. If both are on the same classpath, the build has no
          // way to align versions.
          //
          // Here we have the that problem in that cassandra and spark both use netty.
          //   netty-all is otherwise at 4.0.23, 
          //   but netty-transport is at 4.0.27 via cassandra-driver-core
          //    . netty-transport fails at runtime if netty-common isn't at 4.0.27
          //    . netty-common is in netty-all. 
          //    . fails unless nett-all is later in classpath 
          //   so we bump netty-all to 4.0.27 to manually align things.
          // 
        dependencyOverrides += "io.netty" % "netty-all" % "4.0.27.Final", 
        libraryDependencies ++= logbackIntegrationTest ++ spark ++ Seq(
          sparkRepl
        ),
        assemblyExclusions,
        initialCommands in console := """
          import nest.sg.SparkleConsole._
          import nest.sparkle.store.Event
          import rx.lang.scala.Observable
          import scala.concurrent.duration._
          import nest.sparkle.util.FutureAwait.Implicits._
          import nest.sparkle.datastream.DataArray
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
