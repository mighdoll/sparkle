package nest.sparkle.util

import org.scalatest.{FunSuite, Matchers}

class TestResources extends FunSuite with Matchers {
  test("load sample directory") {
    val resources = Resources.byDirectory("subdir")
    resources.toList should contain ("test.txt")
  }
  
  test("load from .jar file") {
    val classLoader = Thread.currentThread().getContextClassLoader
    val url = classLoader.getResource("test.jar")
    val resources = Resources.childrenFromJar(url, "path/to/here")
    resources.toList should contain ("file")
    resources.toList should contain ("file2")
  }

}