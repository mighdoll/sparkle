package nest.sparkle.time.server

import com.typesafe.config.Config

import unfiltered.netty.websockets._
import unfiltered.request.Path
import nest.sparkle.util.ConfigUtil.configForSparkle
import nest.sparkle.util.Log
import rx.lang.scala.Observable
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import rx.lang.scala.Subscriber

class WebSocketServer(rootConfig:Config) extends Log {
  val subscribers = new ConcurrentHashMap[Subscriber[SocketCallback], Boolean].asScala
  private lazy val http = {
    val port = configForSparkle(rootConfig).getInt("web-socket.port")

    val plan = Planify {
      case Path("/echo") => {
        case Open(socket) =>
          log.info("/echo: open socket")
        case Message(s, Text(m)) =>
          log.info(s"/echo: received message: $m")
          s.send(m)
      }

      case Path("/data") => {
        case open@Open(socket) =>
          log.info("/data: open socket")
          sendToSubscribers(open)
        case message@Message(s, Text(m)) =>
          log.info(s"/data: received message: $m")
          sendToSubscribers(message)
      }
    }
    unfiltered.netty.Http(port)
      .handler(plan)
  }
  
  def sendToSubscribers(msg:SocketCallback) {
    val (expired, current) = subscribers.keys.partition { _.isUnsubscribed}
    subscribers --= expired
    current.foreach { subscriber => subscriber.onNext(msg) }
  }

  def start() {
    http.start()
  }

  def shutdown() {
    http.stop()
  }

  val results: Observable[SocketCallback] = {
    Observable { subscriber =>
      subscribers.put(subscriber, true)
    }
  }

}
