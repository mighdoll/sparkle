package nest.sparkle.test

import java.util.concurrent.atomic.AtomicInteger


/**
 * Unique allocation of tcp ports for test fixtures.
 */
object PortsTestFixture {
  // we give each test its own set of ports, so that multiple tests can run in parallel
  private val nextPort = new AtomicInteger(22000)

  /** get the next tcp port, ports are globally unique for this jvm */
  def takePort(): Int = {
    nextPort.getAndAdd(1)
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
