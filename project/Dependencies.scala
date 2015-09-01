import sbt._

object Dependencies {
  object V {
    val scalaTest = "2.2.1"
    val scalaCheck = "1.12.1" 
    val akka = "2.3.11"
    val spray = "1.3.3"
    val sprayJson = "1.3.2"
    val spark = "1.4.1"
    val slf4j = "1.7.9"
  }

  // To get latest versions
  val slf4j                 = "org.slf4j"                 % "slf4j-api"               % V.slf4j
  val scalaConfig           = "com.typesafe"              %  "config"                 % "1.2.1"

  // Spray + Akka
  val sprayCan              = "io.spray"                  %%  "spray-can"              % V.spray
  val sprayRouting          = "io.spray"                  %%  "spray-routing"          % V.spray
  val sprayClient           = "io.spray"                  %%  "spray-client"           % V.spray
  val sprayCaching          = "io.spray"                  %%  "spray-caching"          % V.spray
  val sprayUtil             = "io.spray"                  %%  "spray-util"             % V.spray
  val sprayJson             = "io.spray"                  %%  "spray-json"             % V.sprayJson
  val akkaActor             = "com.typesafe.akka"         %%  "akka-actor"             % V.akka
  val akkaSlf4j             = "com.typesafe.akka"         %%  "akka-slf4j"             % V.akka
  val akkaRemoting          = "com.typesafe.akka"         %%  "akka-remote"            % V.akka

  val argot                 = "org.clapper"               %% "argot"                  % "1.0.3"
  val nScalaTime            = "com.github.nscala-time"    %% "nscala-time"            % "1.6.0"
  val spire                 = "org.spire-math"            %% "spire"                  % "0.9.0"
  val openCsv               = "net.sf.opencsv"            %  "opencsv"                % "2.3"
  val cassandraAll          = "org.apache.cassandra"      %  "cassandra-all"          % "2.1.8"
  val cassandraDriver       = "com.datastax.cassandra"    %  "cassandra-driver-core"  % "2.1.7.1"
  val snappy                = "org.xerial.snappy"         %  "snappy-java"            % "1.0.5"
  val lz4                   = "net.jpountz.lz4"           %  "lz4"                    % "1.3.0"

  // Note: version is intentionally left out. Usage: `libraryDependencies <+= scalaVersion(scalaReflect % _)`.
  val scalaReflect          = "org.scala-lang"            %  "scala-reflect"
  val scalaLogging          = "com.typesafe.scala-logging" %% "scala-logging-slf4j"    % "2.1.2"

  val rxScala               = "io.reactivex"              %% "rxscala"                % "0.23.1"
  val sparkCassandra        = ("com.datastax.spark"       %% "spark-cassandra-connector" % "1.4.0-M3" withSources() withJavadoc()
                                excludeAll(ExclusionRule(organization = "org.apache.spark"))
                              )
  val sparkCore             = ("org.apache.spark"         %% "spark-core"             % V.spark
                                // a shaded version of minlog included in the kryo jar, so try to exclude this additional copy
                                // however, this doesn't seem to work, so we yank it out of the assembly
                                // LATER revisit in a current rev of spark/kryo. See https://github.com/EsotericSoftware/kryo/issues/189
                                exclude("com.esotericsoftware.minlog", "minlog")
                                exclude("org.slf4j", "slf4j-log4j12")
                              )
  val sparkSql              = ("org.apache.spark"         %% "spark-sql"             % V.spark
                                exclude("org.slf4j", "slf4j-log4j12")
                              )
  val sparkMllib            = ("org.apache.spark"         %% "spark-mllib"            % V.spark
                                exclude("com.typesafe", "scalalogging-slf4j_2.11")   // avoid version conflict (and selected explicitly above anyway)
                                exclude("org.slf4j", "slf4j-log4j12")
                              )
  val sparkRepl             = ("org.apache.spark"          %% "spark-repl"             % V.spark    
                                exclude("org.slf4j", "slf4j-log4j12")
                              )

  val breeze                = "org.scalanlp"               %% "breeze"                 % "0.9"
  // we can add this back to speed up breeze
  val breezeNatives         = ("org.scalanlp"              %% "breeze-natives"         % "0.9"
                                exclude("com.github.fommil.netlib", "all")
                              )
  val nativeNetlibLinux     = "com.github.fommil.netlib" % "netlib-native_system-linux-x86_64" % "1.1" classifier "natives"
  val nativeNetlibOsX       = "com.github.fommil.netlib" % "netlib-native_system-osx-x86_64" % "1.1" classifier "natives"
                              
  val apacheAvro            = "org.apache.avro"           % "avro"                    % "1.7.6"
  // avroMapred comes in a hadoop1 vs. hadoop2 favor
  // All spark 1.1.0 artifacts are compiled against hadoop1 libraries, so we want to the "hadoop1" classifier here
  val apacheAvroMapred      = ("org.apache.avro"          %  "avro-mapred"            % "1.7.6" classifier "hadoop1"
                                exclude("org.slf4j", "slf4j-api")
                                exclude("org.apache.avro", "avro")
                                exclude("org.apache.avro", "avro-ipc")
                                exclude("org.codehaus.jackson", "jackson-core-asl")
                                exclude("org.codehaus.jackson", "jackson-mapper-asl")
                              )

  val apacheKafka           = ("org.apache.kafka"         %% "kafka"                  % "0.8.2.1"
                                  exclude("javax.jms", "jms")
                                  exclude("com.sun.jdmk", "jmxtools")
                                  exclude("com.sun.jmx", "jmxri")
                                  exclude("org.slf4j", "slf4j-simple")
                              )
  val zookeeper             = ("org.apache.zookeeper"      % "zookeeper"              % "3.4.5"
                                  exclude ("org.jboss.netty", "netty")
                                  exclude("javax.jms", "jms")
                                  exclude("com.sun.jdmk", "jmxtools")
                                  exclude("com.sun.jmx", "jmxri")
                              )

  val unfiltered            = "net.databinder"            %% "unfiltered-netty-websockets"  % "0.8.4"
  val sparkNetty            = "io.netty"                  %  "netty"                  % "3.8.0.Final"

  val metricsScala          = "nl.grons"                  %% "metrics-scala"          % "3.5.1"
  val metricsGraphite       = "io.dropwizard.metrics"     %  "metrics-graphite"       % "3.1.2"

  // match version used by spark, cassandra driver, etc
  val guava                 = "com.google.guava"          % "guava"                   % "14.0.1"

  // see https://issues.scala-lang.org/browse/SI-8978
  val jsr                   = "com.google.code.findbugs"  % "jsr305"                  % "2.0.3"

  object Web {
    val d3                  = "org.webjars"               % "d3js"                    % "3.5.5-1"
    val angularMaterial     = "org.webjars"               % "angular-material"        % "0.10.0"
    val angularAMD          = "org.webjars"               % "angularAMD"              % "0.2.1-1"
    val angularTreeControl  = "org.webjars.bower"         % "angular-tree-control"    % "0.2.9"
    val ngFileUpload        = "org.webjars.bower"         % "ng-file-upload"          % "5.0.9"
    val whenJs              = "org.webjars.bower"         % "when"                    % "3.7.3"
  }

  object Runtime {
    val logback             = "ch.qos.logback"            % "logback-classic"         % "1.1.2"
    val log4j               = "log4j"                     % "log4j"                   % "1.2.17"
    val slf4jlog4j          = "org.slf4j"                 % "slf4j-log4j12"           % V.slf4j
    val log4jBridge         = "org.slf4j"                 % "log4j-over-slf4j"        % V.slf4j
  }

  object Test {
    val scalaTest           = "org.scalatest"            %% "scalatest"              % V.scalaTest  % "test"
    val scalaCheck          = "org.scalacheck"           %% "scalacheck"             % V.scalaCheck % "test"
    val sprayTestKit        = "io.spray"                 %% "spray-testkit"          % V.spray      % "test"
    val akkaTestKit         = "com.typesafe.akka"        %% "akka-testkit"           % V.akka       % "test"
  }

  object IT {
    val scalaTest           = "org.scalatest"            %% "scalatest"              % V.scalaTest  % "it"
    val scalaCheck          = "org.scalacheck"           %% "scalacheck"             % V.scalaCheck % "it"
    val sprayTestKit        = "io.spray"                 %% "spray-testkit"          % V.spray      % "it"
    val akkaTestKit         = "com.typesafe.akka"        %% "akka-testkit"           % V.akka       % "it"
    val tubeSocks           = ("me.lessis"               %% "tubesocks"              % "0.1.0"
                                exclude("org.slf4j", "slf4j-jdk14")
                              )
  }
  
  val scalaTest           = "org.scalatest"            %% "scalatest"              % V.scalaTest
  val scalaCheck          = "org.scalacheck"           %% "scalacheck"             % V.scalaCheck
  val sprayTestKit        = "io.spray"                 %% "spray-testkit"          % V.spray
  val akkaTestKit         = "com.typesafe.akka"        %% "akka-testkit"           % V.akka

  object Optional {
    val logback             = Runtime.logback               % "optional"
    val log4j               = Runtime.log4j                 % "optional"
    val slf4jlog4j          = Runtime.slf4jlog4j            % "optional"
    val log4jBridge         = Runtime.log4jBridge           % "optional"
    val metricsGraphite     = Dependencies.metricsGraphite  % "optional"
  }
  

  lazy val integrationTest = Seq(
    IT.scalaTest,
    IT.scalaCheck
  )

  lazy val unitTest = Seq(
    Test.scalaTest,
    Test.scalaCheck
  )

  lazy val kitTests =
    Seq( // test libraries, but in main config, not in Test or IT
      scalaTest,
      scalaCheck,
      akkaTestKit
    )

  lazy val allTest = integrationTest ++ unitTest ++ sprayIntegrationTest

  lazy val sprayTest = Seq(
    Test.sprayTestKit,
    Test.akkaTestKit
  )

  lazy val sprayIntegrationTest = sprayTest ++ Seq(
    IT.sprayTestKit,
    IT.akkaTestKit
  )

  lazy val logging = Seq(
    scalaLogging,
    slf4j
  )

  lazy val logbackIntegrationTest = unitTest ++ integrationTest ++ logging ++ Seq(
    Runtime.logback % "test;it"
  )

  lazy val logbackUnitTest = unitTest ++ logging ++ Seq(
    Runtime.logback % "test"
  )

  lazy val log4jLogging = Seq(
    Runtime.slf4jlog4j, 
    Runtime.log4j
  )

  lazy val kitTestsAndLogging = {
    Seq( // test libraries, but in main config, not in Test or IT
      scalaTest,
      scalaCheck,
      sprayTestKit,
      akkaTestKit
    ) ++ logging
  }

  lazy val spray = Seq(
    sprayJson,
    sprayClient,
    sprayRouting,
    sprayCan,
    sprayCaching
  )

  lazy val kafka = Seq(apacheKafka)

  lazy val avro = Seq(apacheAvro)

  lazy val akka = Seq(
    akkaActor,
    akkaRemoting,
    akkaSlf4j
  )

  lazy val cassandraClient = Seq(
    cassandraDriver,
    snappy,
    lz4
  )

  lazy val nativeMath = Seq(
    breeze
  )

  lazy val spark = Seq(
    sparkCassandra,
    sparkMllib,
    sparkCore,
    sparkSql,
    sparkNetty
  ) ++ nativeMath

  lazy val web = {
    import Web._
    Seq(
      d3,
      angularMaterial,
      angularAMD,
      angularTreeControl,
      ngFileUpload,
      whenJs
    )
  }

  // For setting minimum versions
  lazy val versionOverrides = Set(
    slf4j,
    Runtime.log4j
  )

}
