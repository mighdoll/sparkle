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

object Dependencies {
  object V {
    val scalaTest = "2.1.0"
    val akka = "2.3.0"
    val spray = "1.3.1"
    val rxJava = "0.17.6"
  }

  // Spray + Akka
  val sprayCan              = "io.spray"                  %  "spray-can"              % V.spray
  val sprayRouting          = "io.spray"                  %  "spray-routing"          % V.spray
  val sprayClient           = "io.spray"                  %  "spray-client"           % V.spray
  val sprayCaching          = "io.spray"                  %  "spray-caching"          % V.spray
  val sprayJson             = "io.spray"                  %% "spray-json"             % "1.2.5"
  val akkaActor             = "com.typesafe.akka"         %% "akka-actor"             % V.akka
  val akkaSlf4j             = "com.typesafe.akka"         %% "akka-slf4j"             % V.akka
  val akkaRemoting          = "com.typesafe.akka"         %% "akka-remote"            % V.akka

  val argot                 = "org.clapper"               %% "argot"                  % "1.0.1"
  val nScalaTime            = "com.github.nscala-time"    %% "nscala-time"            % "1.0.0"
  val spire                 = "org.spire-math"            %% "spire"                  % "0.7.4"
  val openCsv               = "net.sf.opencsv"            %  "opencsv"                % "2.3"
  val cassandraAll          = "org.apache.cassandra"      % "cassandra-all"           % "2.0.3"
  val cassandraDriver       = "com.datastax.cassandra"    % "cassandra-driver-core"   % "2.0.0"
  val snappy                = "org.xerial.snappy"         % "snappy-java"             % "1.0.5"
  val lz4                   = "net.jpountz.lz4"           % "lz4"                     % "1.2.0"

  val scalaReflect          = "org.scala-lang"            % "scala-reflect"           % "2.10.3"

  val rxJavaCore            = "com.netflix.rxjava"        % "rxjava-core"             % V.rxJava
  val rxJavaScala           = "com.netflix.rxjava"        % "rxjava-scala"            % V.rxJava  intransitive()
  val scalaLogging          = "com.typesafe"              %% "scalalogging-slf4j"     % "1.0.1"
  val apacheAvro            = "org.apache.avro"           % "avro"                    % "1.7.6"
  val apacheKafka           = ("org.apache.kafka"         %% "kafka"                  % "0.8.0"
                                  exclude("javax.jms", "jms")
                                  exclude("com.sun.jdmk", "jmxtools")
                                  exclude("com.sun.jmx", "jmxri")
                                  exclude("org.slf4j", "slf4j-simple")
                              )

  object Runtime {
    val logback              = "ch.qos.logback"            % "logback-classic"         % "1.0.9"
  }

  object Test {
    val scalaTest            = "org.scalatest"            %% "scalatest"              % V.scalaTest % "test"
    val scalaCheck           = "org.scalacheck"           %% "scalacheck"             % "1.11.3"    % "test"
    val sprayTestKit         = "io.spray"                 %  "spray-testkit"          % V.spray     % "test"
    val akkaTestKit          = "com.typesafe.akka"        %% "akka-testkit"           % V.akka      % "test"
  }

  object IT {
    val scalaTest            = "org.scalatest"            %% "scalatest"              % V.scalaTest % "it"
    val scalaCheck           = "org.scalacheck"           %% "scalacheck"             % "1.11.3"    % "it"
    val sprayTestKit         = "io.spray"                 %  "spray-testkit"          % V.spray     % "it"
    val akkaTestKit          = "com.typesafe.akka"        %% "akka-testkit"           % V.akka      % "it"
  }

  val basicTest = Seq(
    Test.scalaTest,
    Test.scalaCheck,
    IT.scalaTest,
    IT.scalaCheck
  )

  val logging = Seq(
    scalaLogging,
    Runtime.logback
  )

  val testAndLogging = basicTest ++ logging


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

}
