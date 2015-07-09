name := "sparkle_project"

scalacOptions += "-deprecation" // warnings when compiling project files

resolvers ++= Seq(
  Resolver.url(
    "bintray-sbt-plugin-releases",
      url("http://dl.bintray.com/content/sbt/sbt-plugin-releases")
  )(Resolver.ivyStylePatterns)
)

libraryDependencies ++= Seq(
  "org.slf4j"                %  "slf4j-simple"           % "1.7.9",
  "com.datastax.cassandra"   %  "cassandra-driver-core"  % "2.1.3",
  "net.jpountz.lz4"          %  "lz4"                    % "1.3.0",
  "org.apache.kafka"         %% "kafka"                  % "0.8.1.1"
                                  exclude("javax.jms", "jms")
                                  exclude("com.sun.jdmk", "jmxtools")
                                  exclude("com.sun.jmx", "jmxri")
                                  exclude("org.slf4j", "slf4j-simple")
)

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.1")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.12.0")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.5.0")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.8")

// for quick rebuilds of any scala service, not needed for deployed builds, but handy for development
addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

// for scalastyle checks
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.7.0")

// for doc html page 
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.8.1")
