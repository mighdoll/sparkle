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
  import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys
  import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseCreateSrc
  import Dependencies._
  import sbtassembly.Plugin.AssemblyKeys._
  import spray.revolver.RevolverPlugin._
  import sbtrelease.ReleasePlugin._
  import bintray.Plugin._

  lazy val sparkleSettings = Defaults.defaultSettings ++
    sbtassembly.Plugin.assemblySettings ++
    Seq(
      organization := "nest",
      EclipseKeys.withSource := true,
      EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource,

      scalaVersion := "2.10.4-RC1",
      scalaBinaryVersion := "2.10",
//      testOptions += Tests.Argument("-oF"),    // show full stack traces
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings", "-language:postfixOps")
    )

  private lazy val itSettings = Defaults.itSettings ++ Seq(
    // include integration test code (src/it) in generated eclipse projects
    EclipseKeys.configurations := Set(sbt.Compile, sbt.Test, sbt.IntegrationTest)
  )

  lazy val sparkleTime = 
    Project(id = "sparkle-time", base = file("."))
      .configs(IntegrationTest)
      .settings(bintraySettings:_*)
      .settings(MavenPublish.settings:_*)
      .settings(itSettings: _*)
      .settings(sparkleSettings: _*)
      .settings(Revolver.settings: _*)
      .settings(releaseSettings: _*)
      .settings(
        libraryDependencies ++= Seq(
          scalaReflect,
          akkaActor,
          akkaRemoting,
          akkaSlf4j,
          cassandraClient,
          sprayJson,
          sprayClient,
          sprayRouting,
          sprayCan,
          sprayCaching,
          rxJavaCore,
          rxJavaScala,
          nScalaTime,
          argot,
          openCsv,
          Runtime.logback,
          Test.scalaTest,
          Test.scalaCheck,
          Test.sprayTestKit,
          Test.akkaTestKit,   // delete this after spray #446 is resolved 
          IT.sprayTestKit,
          IT.akkaTestKit,   // delete this after spray #446 is resolved 
          IT.scalaTest,
          IT.scalaCheck
         ),
        licenses += ("Apache-2.0", url("http://apache.org/licenses/LICENSE-2.0.html"))
      )

}
