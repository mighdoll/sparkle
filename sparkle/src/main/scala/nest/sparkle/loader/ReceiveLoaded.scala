package nest.sparkle.loader
import scala.concurrent.{ Future, Promise }
import akka.actor.{Actor, Props}
import scala.util.Success
import nest.sparkle.util.Log


/** Constructor for a ReceiveLoaded actor */
object ReceiveLoaded {
  def props(targetPath: String, complete: Promise[Unit]): Props =
    Props(classOf[ReceiveLoaded], targetPath, complete)
}

/** An actor that completes a future when a LoadComplete message is received */
class ReceiveLoaded(targetPath: String, complete: Promise[Unit]) extends Actor with Log {
  def receive = {
    case LoadComplete(path) if path == targetPath =>
      log.debug(s"ReceiveLoaded. found columnPath: $path")
      complete.complete(Success())
    case FileLoadComplete(path) if path == targetPath =>
      log.debug(s"ReceiveLoaded. found file relative path: $path")
      complete.complete(Success())
    case x => log.trace(s"ReceiveLoaded filter skipping path: $x")
  }
}
