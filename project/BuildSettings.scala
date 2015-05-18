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

import sbtrelease.ReleasePlugin
import spray.revolver.RevolverPlugin._

import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseCreateSrc

import sbtassembly.AssemblyPlugin.autoImport._

import BackgroundServiceKeys._

object BuildSettings {

  lazy val sparkleSettings =
    orgSettings ++
    compileSettings ++
    eclipseSettings ++
    itSettingsWithEclipse ++
    slf4jSettings ++
    testSettings ++
    publishSettings ++
    dependencySettings ++
    org.scalastyle.sbt.ScalastylePlugin.Settings

  lazy val orgSettings = Seq(
    organization := "nest",
    licenses += ("Apache-2.0", url("http://apache.org/licenses/LICENSE-2.0.html"))
  )

  lazy val compileSettings = Seq(
    scalaVersion := "2.11.6",
    crossScalaVersions := Seq("2.10.5"),
    // TODO(ochafik): Do we need a custom binary version?
    // scalaBinaryVersion <<= scalaVersion { scalaVersion => 
    //   val Rx = """(\d+\.\d+)(?:\.\d+)""".r
    //   val Rx(major) = scalaVersion
    //   major
    // },
    testOptions += Tests.Argument("-oF"), // show full stack traces during test failures
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings", "-language:postfixOps", "-target:jvm-1.7")
  )

  lazy val eclipseSettings = Seq(
    EclipseKeys.withSource := true,
    EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource,
//    EclipseKeys.withBundledScalaContainers := false,   // doesn't work well with 4.4, e.g. scalatest plugin fails
    EclipseKeys.eclipseOutput := Some("eclipse-target")
  )

  lazy val itSettingsWithEclipse = Defaults.itSettings ++ Seq(
    // include integration test code (src/it) in generated eclipse projects
    EclipseKeys.configurations := Set(sbt.Compile, sbt.Test, sbt.IntegrationTest)
  )

  lazy val testSettings = Seq(
    parallelExecution in test in IntegrationTest := false // cassandra driver (2.0.[01]) seems to have trouble with multiple keyspaces..
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
      ReleasePlugin.releaseSettings ++
      MavenPublish.settings ++
      Revolver.settings  // TODO Revolver isn't really 'publish' settings, and BackgroundService includes revolver
  
  lazy val dependencySettings = Seq(
    updateOptions := updateOptions.value.withCachedResolution(true),
    dependencyOverrides ++= Dependencies.dependencyOverrides
  )


  lazy val dataServerMergeSettings = Seq(
    assemblyMergeStrategy in assembly <<= (assemblyMergeStrategy in assembly) { old =>
      { case "META-INF/io.netty.versions.properties" => MergeStrategy.first
        case x => old(x)
      }
    }
  )

  // hopefully this is unnecessary in the current rev of spark, 
  // (if not, we should pick winners/exclusions more carefully)
  lazy val sparkMergeSettings = Seq(
    assemblyMergeStrategy in assembly <<= (assemblyMergeStrategy in assembly) { old => 
      { case "META-INF/mailcap"                                      => MergeStrategy.first
        case n if n.startsWith("META-INF/maven/org.slf4j/slf4j-api") => MergeStrategy.first
        case n if n.endsWith("linux32/libjansi.so")                  => MergeStrategy.discard
        case n if n.endsWith("jansi.dll")                            => MergeStrategy.discard
        case n if n.endsWith("linux64/libjansi.so")                  => MergeStrategy.last  // TODO fix better
        case n if n.endsWith("osx/libjansi.jnilib")                  => MergeStrategy.last  // TODO fix better
        case n if n.startsWith("org/apache/commons/beanutils/")      => MergeStrategy.last  // TODO fix better
        case n if n.startsWith("org/apache/commons/collections/")    => MergeStrategy.last  // TODO fix better
        case n if n.startsWith("org/apache/commons/logging/")        => MergeStrategy.last  // TODO fix better
        case n if n.startsWith("org/fusesource/hawtjni/runtime/")    => MergeStrategy.last  // TODO fix better
        case n if n.startsWith("org/fusesource/jansi/")              => MergeStrategy.last  // TODO fix better
        case "plugin.properties"                                     => MergeStrategy.first // TODO fix better
        case x                                                       => old(x)
      }
    }
  )

  /** settings so that we launch our favorite Main by default */
  def setMainClass(className: String): Seq[Setting[_]] = {
    Seq(
      mainClass in Revolver.reStart := Some(className),
      mainClass in assembly := Some(className),
      mainClass in (Compile, run):= Some(className)
    )
  }
}
