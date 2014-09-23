package nest.sparkle.util

import scala.concurrent.duration._

import org.scalatest.{FunSuite, Matchers}

/**
 * Add description here.
 *
 * @author David Korz
 * @version 0.1
 */
class TestRetryManager 
  extends FunSuite 
  with Matchers
{
  
  test("Execute should succeed on first try") {
    val mgr = RetryManager(10 milliseconds,1 seconds)
    val answer = 42
    
    def success: Int = {
      answer
    }
    
    val result = mgr.execute[Int](success)
    
    result shouldBe answer
  }
  
  test("Execute should try 5 times") {
    val mgr = RetryManager(10 milliseconds,1 seconds)
    val answer = 42
    var count = 0
    val N = 5
    
    def failN: Int = {
      if (count == N) {
        answer
      } else {
        count += 1
        throw new IllegalArgumentException("first time")
      }
    }
    
    val result = mgr.execute[Int](failN)
    
    result shouldBe answer
    count shouldBe 5
  }
  
  test("Fatal error should be thrown") {
    val mgr = RetryManager(10 millis,1 seconds)
    
    def die: Int = {
      throw new InterruptedException("die")
    }
    
    intercept[InterruptedException] { mgr.execute[Int](die) }
  }

}
