package nest.sparkle.store.cassandra


import com.datastax.driver.core.ResultSet
import scala.concurrent.Future
import rx.lang.scala.Observable
import rx.lang.scala.Observer
import rx.lang.scala.Subscription
import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._
import com.datastax.driver.core.Row
import nest.sparkle.measure.{Span, StartedSpan}
import nest.sparkle.util.GuavaConverters._
import scala.annotation.tailrec
import com.datastax.driver.core.ResultSetFuture
import rx.lang.scala.Subscriber
import nest.sparkle.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import nest.sparkle.util.Log

object ObservableResultSetA {
  /** a ResultSetFuture that can be converted into an Observable for asynchronously
    * working with the stream of Rows as the arrive from the database
    */
  implicit class WrappedResultSet(val resultSetFuture: ResultSetFuture) extends Log {

    /** return an Observable[Row] for the Future[ResultSet].  */
    def observerableRowsA // format: OFF
        ( parentSpan: StartedSpan )
        ( implicit executionContext: ExecutionContext )
        : Observable[Seq[Row]] = { // format: ON

      def nextFetch() = Span.start("fetchBlock", parentSpan)
      var currentFetch = nextFetch()

      val asScalaFuture = resultSetFuture.toFuture
      val subscribed = new AtomicBoolean

      /** A constructor function for making an Observable.  The function takes an Observer to which it
        * feeds rows as they arrive.  It returns a Subscription so that the Observer can can abort the stream
        * early if necessary.
        */
      Observable { subscriber: Subscriber[Seq[Row]] =>
        if (subscribed.compareAndSet(false, true)) {
          asScalaFuture.foreach { resultSet =>
            /** Iterate through the rows as they arrive from the network, calling observer.onNext for each row.
              *
              * rowChunk() is called once for each available group ('chunk') of resultSet rows.  It
              * recursively calls itself to process the next fetched set of rows until there are now more rows left.
              */
            def rowChunk(): Unit = {
              if (!subscriber.isUnsubscribed) {
                val iterator = resultSet.iterator().asScala
                val availableNow = resultSet.getAvailableWithoutFetching()
                val rows = iterator.take(availableNow).toVector
                currentFetch.complete()
                subscriber.onNext(rows)
                
                if (resultSet.isFullyFetched()) { // CONSIDER - is this a race with availableNow?
                  parentSpan.complete()
                  subscriber.onCompleted()
                } else {
                  currentFetch = nextFetch()
                  resultSet.fetchMoreResults().toFuture.foreach { _ => rowChunk() }
                }
              }
            }

            rowChunk()
          }

          asScalaFuture.onFailure {
            case error: Throwable =>
              subscriber.onError(error)
          }
        } else {
          log.error(s"only one subscription allowed to each ObservableResultSet: $resultSetFuture")
          Observable.empty
        }
      }

    }

  }
}
