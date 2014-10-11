import sbt._
import sbt.Def.Initialize

object SbtUtil {
  
  /** Return a task that will execute the provided tasks sequentially.  */
  def inOrder(tasks: Initialize[Task[Unit]]*): Initialize[Task[Unit]] = {
    tasks match {
      case Seq()         => Def.task { () }
      case Seq(x, xs@_*) => Def.taskDyn{ val _ = x.value; inOrder(xs: _*) }
    }
  }

}