package nest.sparkle.metrics

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.BufferedWriter
import java.nio.charset.Charset

/**
 * Subclass Graphite to write to an OutputStream instead of a socket.
 * @param stream Place to write output.
 */
class Graphite(stream: OutputStream) 
  extends com.codahale.metrics.graphite.Graphite(null) 
{
  val charset = "UTF-8"
  private var out: BufferedWriter = null
  
  override def connect() {
    if (out != null) throw new IllegalStateException("Already connected")
    out = new BufferedWriter(new OutputStreamWriter(stream, Charset.forName(charset)))
  }
  
  override def close() {
    if (out != null) {
      out.close()
      out = null
    }
  }
  
  override def send(name: String, value: String, timestamp: Long) {
    out.write(sanitize(name))
    out.write(' ')
    out.write(sanitize(value))
    out.write(' ')
    out.write(timestamp.toString)
    out.write('\n')
    out.flush()
  }
}
