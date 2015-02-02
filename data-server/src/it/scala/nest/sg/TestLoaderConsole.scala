package nest.sg

import scala.concurrent.Promise
import scala.util.Success

import org.scalatest.{Matchers, FunSuite}

import nest.sparkle.store.DirectoryLoaded
import nest.sparkle.util.Resources
import nest.sparkle.util.FutureAwait.Implicits._

class TestLoaderConsole extends FunSuite with Matchers with SparkleConsoleFixture {
  val measuresDirectory = Resources.filePathString("sample-measures")

  def testLoad(fn:SparkleConsole => Unit): Unit = {
    withSparkleConsole { console =>
      val done = Promise[Unit]()
      console.store.writeListener.listen(measuresDirectory).subscribe{ event =>
        event match {
          case DirectoryLoaded(`measuresDirectory`) => done.complete(Success(Unit))
          case x                                    => 
        }
      }
      fn(console)
      done.future.await
    }
  }

  test("loadFiles") {
    testLoad(_.loadFiles(measuresDirectory))
  }


  test("watchFiles") {
    testLoad(_.watchFiles(measuresDirectory))
  }
}
