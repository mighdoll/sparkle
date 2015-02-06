package nest.sparkle.test


/**
 * Unique allocation of tcp ports for test fixtures.
 */
object PortsTestFixture {
  // we give each test its own set of ports, so that multiple tests can run in parallel
  private var nextPort = 22222

  /** get the next tcp port, ports are globally unique for this jvm */
  def takePort():Int = {
    val port = nextPort
    nextPort += 1
    port
  }


  /** return configuration overrides so that a test will run on a unique set of ports */
  def sparklePortsConfig():Seq[(String, Any)] = {
    Seq(
      "sparkle.port" -> takePort(),
      "sparkle.admin.port" -> takePort(),
      "sparkle.web-socket.port" -> takePort()
    )
  }

}
