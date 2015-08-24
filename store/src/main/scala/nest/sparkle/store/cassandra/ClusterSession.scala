package nest.sparkle.store.cassandra

import com.datastax.driver.core.Cluster
import com.datastax.driver.core.Session
import java.io.Closeable

/** Bundle a cassandra cluster connection and session on that cluster together,
 *  so we can close them together.  */
case class ClusterSession(cluster:Cluster, session:Session) extends Closeable {
  def close(): Unit = {
    session.close() // maybe unnecessary given cluster.close() below
    cluster.close()
  }
}
