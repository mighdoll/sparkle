package nest.sparkle.store

import scala.collection.JavaConverters._
import scala.collection.mutable
import java.util.concurrent.ConcurrentHashMap

import rx.lang.scala.{Observable, Subject}

import nest.sparkle.util.Log

/** Message sent to WriteListener */
sealed abstract class WriteEvent

/** Sent to WriteListener, describes portion of data written */ // TODO DRY with loader.ColumnUpdate
case class ColumnUpdate[T](start: T, end: T) extends WriteEvent

/** Sent to WriteListener when a file has been loaded into the store */
case class FileLoaded(fileName:String) extends WriteEvent

/** Sent to WriteListener when all the files in a directory have been loaded */
case class DirectoryLoaded(directory:String) extends WriteEvent

/** Sent to WriteListener, emitted when a listen is first registered */
object ListenRegistered extends WriteEvent

/** api to listen for column updates on a given column path */
trait WriteListener {
  /** return an observable that reports on every write. 
   *  The type T is the type of the keys in the column for ColumnUpdate messages */
  def listen(path: String): Observable[WriteEvent]
}

/** api to produce column updates on a given column path */
trait WriteNotifier {
  def columnUpdate[T](columnPath: String, columnUpdate: ColumnUpdate[T])  
  def fileLoaded(fileName:String)
  def directoryLoaded(directory:String)
}

trait WriteListenNotify extends WriteListener with WriteNotifier

/** publish and subscribe for updates to column */
class WriteNotification() extends WriteListenNotify with Log {
  val subjects = new ConcurrentHashMap[String, mutable.Set[Subject[WriteEvent]]].asScala

  override def listen(columnPath: String): Observable[WriteEvent] = {
    val subject = Subject[WriteEvent]()
    log.trace(s"registered listen for $columnPath via $subject")
    val initialSet = mutable.Set(subject)
    val alreadySet = subjects.putIfAbsent(columnPath, initialSet)
    alreadySet.foreach { set => set += subject}
    subject
  }

  override def columnUpdate[T](columnPath: String, columnUpdate: ColumnUpdate[T]) {
    notifyListeners(columnPath, columnUpdate)
  }
  
  def notifyListeners[T](name:String, writeEvent:WriteEvent) {
    subjects.get(name).foreach { set =>
      set.foreach { subject =>
        log.trace(s"notifying one $name listener of $writeEvent")
        subject.onNext(writeEvent)
      }
    }
  }
  
  override def fileLoaded(fileName:String) {
    val loaded = FileLoaded(fileName)
    notifyListeners(fileName, loaded)
  }
  
  override def directoryLoaded(directory:String) {
    val loaded = DirectoryLoaded(directory)
    notifyListeners(directory, loaded)
  }

  // LATER enable collection (and deletion from the map) of the subscribed/subjects
  // if they are unused. ..Perhaps a timeout and a Subject wrapper that reports whether
  // there are active subscriptions..

  // LATER emit UpdateRegistered after the listen is started, so that clients can know they're
  // not missing anything after that point

  // LATER route listen/notify remotely, probably over kafka
}