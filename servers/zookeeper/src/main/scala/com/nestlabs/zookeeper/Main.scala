package com.nestlabs.zookeeper

import org.apache.log4j.Logger
import org.apache.log4j.jmx.LoggerDynamicMBean

import org.apache.zookeeper.server.{ZooKeeperServerMain, ServerConfig}
import org.apache.zookeeper.server.quorum.QuorumPeerConfig

import java.util.Properties

object Main {
  private val logger = Logger.getLogger(Main.getClass)

  def main(args: Array[String]): Unit = {

    val config = new QuorumPeerConfig()

    val props = Option(this.getClass().getClassLoader().getResourceAsStream("server.properties")).map { propStream => {
      try {
        val props = new Properties()
        props.load(propStream)
        props
      } finally {
        propStream.close()
      }
    }}.getOrElse { sys.error("no server.properties file found on classpath") }

    config.parseProperties(props)

    val serverConfig = new ServerConfig()
    serverConfig.readFrom(config)
    new ZooKeeperServerMain().runFromConfig(serverConfig)
  }
}
