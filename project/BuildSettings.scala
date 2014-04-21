/* Copyright 2014  Nest Labs

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
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseCreateSrc
import sbtassembly.Plugin.AssemblyKeys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseCreateSrc
import sbtassembly.Plugin.AssemblyKeys._
import spray.revolver.RevolverPlugin._
import sbtrelease.ReleasePlugin
import bintray.Plugin._

object BuildSettings {

  lazy val allSettings = 
    Defaults.defaultSettings ++
    orgSettings ++
    compileSettings ++
    eclipseSettings ++
    itSettingsWithEclipse ++
    slf4jSettings ++
    testSettings ++
    publishSettings
    
  lazy val orgSettings = Seq(
    organization := "nest",
    licenses += ("Apache-2.0", url("http://apache.org/licenses/LICENSE-2.0.html"))
  )

  lazy val compileSettings = Seq(
    scalaVersion := "2.10.4",
    scalaBinaryVersion := "2.10",
    testOptions += Tests.Argument("-oF"),    // show full stack traces during test failures
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings", "-language:postfixOps", "-target:jvm-1.7")
  )
    
  lazy val eclipseSettings = Seq(
    EclipseKeys.withSource := true,
    EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource
  )

  lazy val itSettingsWithEclipse = Defaults.itSettings ++ Seq(
    // include integration test code (src/it) in generated eclipse projects
    EclipseKeys.configurations := Set(sbt.Compile, sbt.Test, sbt.IntegrationTest)
  )

  lazy val testSettings = Seq(
    parallelExecution in test in IntegrationTest:= false  // cassandra driver (2.0.[01]) seems to have trouble with multiple keyspaces..
//    fork in IntegrationTest := true // LATER clean up tests better so that this is unnecessary
  )


  lazy val slf4jSettings = Seq(
    // see http://stackoverflow.com/questions/7898273/how-to-get-logging-working-in-scala-unit-tests-with-testng-slf4s-and-logback
    testOptions += Tests.Setup(cl =>
      cl.loadClass("org.slf4j.LoggerFactory").
        getMethod("getLogger", cl.loadClass("java.lang.String")).
        invoke(null, "ROOT")
    )
  )
  
  lazy val publishSettings = 
    // bintraySettings ++ // disabled pending https://github.com/softprops/bintray-sbt/issues/18
    sbtassembly.Plugin.assemblySettings ++
    ReleasePlugin.releaseSettings ++
    MavenPublish.settings ++
    Revolver.settings

}
