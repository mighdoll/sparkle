import sbt._

object Dependencies {
  object V {
    val scalaTest = "2.2.1"
    val scalaCheck = "1.12.1" 
    val akka = "2.2.3"
    val spray = "1.2.1"
    val scala = "2.10.4"
    val spark = "1.1.0"
    val slf4j = "1.7.9"
  }

  // To get latest versions
  val slf4j                 = "org.slf4j"                 % "slf4j-api"               % V.slf4j
  val scalaConfig           = "com.typesafe"              %  "config"                 % "1.2.1"

  // Spray + Akka
  val sprayCan              = "io.spray"                  %  "spray-can"              % V.spray
  val sprayRouting          = "io.spray"                  %  "spray-routing"          % V.spray
  val sprayClient           = "io.spray"                  %  "spray-client"           % V.spray
  val sprayCaching          = "io.spray"                  %  "spray-caching"          % V.spray
  val sprayUtil             = "io.spray"                  %  "spray-util"             % V.spray
  val sprayJson             = "io.spray"                  %% "spray-json"             % "1.2.5"
  val akkaActor             = "com.typesafe.akka"         %% "akka-actor"             % V.akka
  val akkaSlf4j             = "com.typesafe.akka"         %% "akka-slf4j"             % V.akka
  val akkaRemoting          = "com.typesafe.akka"         %% "akka-remote"            % V.akka

  val argot                 = "org.clapper"               %% "argot"                  % "1.0.3"
  val nScalaTime            = "com.github.nscala-time"    %% "nscala-time"            % "1.6.0"
  val spire                 = "org.spire-math"            %% "spire"                  % "0.9.0"
  val openCsv               = "net.sf.opencsv"            %  "opencsv"                % "2.3"
  val cassandraAll          = "org.apache.cassandra"      %  "cassandra-all"          % "2.1.0"
  val cassandraDriver       = "com.datastax.cassandra"    %  "cassandra-driver-core"  % "2.1.3"
  val snappy                = "org.xerial.snappy"         %  "snappy-java"            % "1.0.5"
  val lz4                   = "net.jpountz.lz4"           %  "lz4"                    % "1.3.0"

  val scalaReflect          = "org.scala-lang"            %  "scala-reflect"           % V.scala
  val scalaLogging          = "com.typesafe"              %% "scalalogging-slf4j"     % "1.1.0"

  val rxScala               = "io.reactivex"              %% "rxscala"                % "0.23.0"
  val sparkCassandra        = ("com.datastax.spark"       %% "spark-cassandra-connector" % "1.1.0" withSources() withJavadoc()
                                excludeAll(ExclusionRule(organization = "org.apache.spark"))
                              )
  val sparkCore             = ("org.apache.spark"         %% "spark-core"             % V.spark
                                exclude("org.slf4j", "slf4j-log4j12")
                              )
  val sparkSql              = ("org.apache.spark"         %% "spark-sql"             % V.spark
                                exclude("org.slf4j", "slf4j-log4j12")
                              )
  val sparkMllib            = ("org.apache.spark"         %% "spark-mllib"            % V.spark
                                exclude("com.typesafe", "scalalogging-slf4j_2.10")   // avoid version conflict (and selected explicitly above anyway)
                                exclude("org.slf4j", "slf4j-log4j12")
                              )
  val sparkRepl             = ("org.apache.spark"          %% "spark-repl"             % V.spark          )
                              /* // TODO are these excludes necessary?
                                exclude("com.google.guava", "guava")  
                                exclude("org.apache.spark", "spark-core_2.10") 
                                exclude("org.apache.spark", "spark-bagel_2.10") 
                                exclude("org.apache.spark", "spark-mllib_2.10") 
                                exclude("org.scala-lang", "scala-compiler")          
                              */

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

  val apacheKafka           = ("org.apache.kafka"         %% "kafka"                  % "0.8.1.1"
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

  val unfiltered            = "net.databinder"            %% "unfiltered-netty-websockets"  % "0.8.0" 
  val nettyAll              = "io.netty"                  %  "netty-all"                    % "4.0.19.Final" 
  
  val metricsScala          = "nl.grons"                  %% "metrics-scala"          % "3.2.1_a2.2"
  val metricsGraphite       = "com.codahale.metrics"      %  "metrics-graphite"       % "3.0.2"

  // match version used by spark, cassandra driver, etc
  val guava                 = "com.google.guava"          % "guava"                   % "14.0.1"

  object Runtime {
    val logback             = "ch.qos.logback"            % "logback-classic"         % "1.1.2"
    val log4j               = "log4j"                     % "log4j"                   % "1.2.17"
    val slf4jlog4j          = "org.slf4j"                 % "slf4j-log4j12"           % V.slf4j
    val log4jBridge         = "org.slf4j"                 % "log4j-over-slf4j"        % V.slf4j
  }

  object Test {
    val scalaTest           = "org.scalatest"            %% "scalatest"              % V.scalaTest  % "test"
    val scalaCheck          = "org.scalacheck"           %% "scalacheck"             % V.scalaCheck % "test"
    val sprayTestKit        = "io.spray"                 %  "spray-testkit"          % V.spray      % "test"
    val akkaTestKit         = "com.typesafe.akka"        %% "akka-testkit"           % V.akka       % "test"
  }

  object IT {
    val scalaTest           = "org.scalatest"            %% "scalatest"              % V.scalaTest  % "it"
    val scalaCheck          = "org.scalacheck"           %% "scalacheck"             % V.scalaCheck % "it"
    val sprayTestKit        = "io.spray"                 %  "spray-testkit"          % V.spray      % "it"
    val akkaTestKit         = "com.typesafe.akka"        %% "akka-testkit"           % V.akka       % "it"
  }
  
  object Kit {
    val scalaTest           = "org.scalatest"            %% "scalatest"              % V.scalaTest
    val scalaCheck          = "org.scalacheck"           %% "scalacheck"             % V.scalaCheck
    val sprayTestKit        = "io.spray"                 %  "spray-testkit"          % V.spray
    val akkaTestKit         = "com.typesafe.akka"        %% "akka-testkit"           % V.akka
  }
  
  object Optional {
    val logback             = Runtime.logback               % "optional"
    val log4j               = Runtime.log4j                 % "optional"
    val slf4jlog4j          = Runtime.slf4jlog4j            % "optional"
    val log4jBridge         = Runtime.log4jBridge           % "optional"
    val metricsGraphite     = Dependencies.metricsGraphite  % "optional"
  }
  

  val basicTest = Seq(
    Test.scalaTest,
    Test.scalaCheck,
    IT.scalaTest,
    IT.scalaCheck
  )
  
  val allTest = Seq(
    Test.scalaTest,
    Test.scalaCheck,
    Test.sprayTestKit,
    Test.akkaTestKit,
    IT.scalaTest,
    IT.scalaCheck,
    IT.sprayTestKit,
    IT.akkaTestKit
  )
  
  // For setting minimum versions
  val dependencyOverrides = Set(
      slf4j,
      Runtime.log4j
  )

  val logging = Seq(
    scalaLogging,
    slf4j
  )
  
  val log4jLogging = Seq(Runtime.slf4jlog4j, Runtime.log4j)
  
  val logbackLogging = Seq(Runtime.logback)

  val testAndLogging = basicTest ++ logging

  val kitTestsAndLogging = {
    Seq( // test libraries, but in main config, not in Test or IT
      Kit.scalaTest,
      Kit.scalaCheck,
      Kit.sprayTestKit,
      Kit.akkaTestKit
    ) ++ logging
  }

  val spray = Seq(
    sprayJson,
    sprayClient,
    sprayRouting,
    sprayCan,
    sprayCaching,
    Test.sprayTestKit,
    Test.akkaTestKit, // delete this after spray #446 is resolved
    IT.sprayTestKit,
    IT.akkaTestKit // delete this after spray #446 is resolved
  )

  val kafka = Seq(apacheKafka)

  val avro = Seq(apacheAvro)

  val akka = Seq(
    akkaActor,
    akkaRemoting,
    akkaSlf4j
  )

  val cassandraClient = Seq(
    cassandraDriver,
    snappy,
    lz4
  )

  val nativeMath = Seq(
    breeze
  )

  val spark = Seq(
    sparkCassandra,
    sparkMllib,
    sparkCore,
    sparkSql
  ) ++ nativeMath
 

}
