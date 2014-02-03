name := "sparkle_time_project"

scalacOptions += "-deprecation" // warnings when compiling project files

resolvers ++= Seq(
  Resolver.url(
    "bintray-sbt-plugin-releases",
      url("http://dl.bintray.com/content/sbt/sbt-plugin-releases")
  )(Resolver.ivyStylePatterns)
)

libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.5"

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.1")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.10.2")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.2")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.4.0")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.8")

// for quick rebuilds of any scala service, not needed for deployed builds, but handy for development
addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.1")

