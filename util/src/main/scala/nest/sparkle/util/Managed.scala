package nest.sparkle.util

import java.io.Closeable

/** A simple managed resource.  Use it safely like this:
  * for {
  *   resource <- GetResource()
  * } {
  *   useResource(resource)
  * }
  *
  * The resource will be close()d after it's used.
  */
object Managed { // LATER which SCALA ARM library to use?
  object implicits {
    def managed[T <: Closeable](resource: T): Resource[T] = {
      new Resource(resource)
    }
  }

  class Resource[T <: Closeable](resource: T) {
    def foreach(fn: T => Unit): Unit = {
      try {
        fn(resource)
      } finally {
        resource.close()
      }
    }
  }
}

