package nest.sparkle.util.kafka

import java.util.concurrent.{SynchronousQueue, TimeUnit, ThreadPoolExecutor}

import scala.concurrent.{ExecutionContext, Future, future}
import scala.concurrent.duration._

import org.I0Itec.zkclient.ZkClient

import kafka.utils.ZkUtils
import kafka.utils.ZKStringSerializer

import nest.sparkle.util.Log

/** Functions to get data about Kafka topics.
 */
class Utils(
  val connectString: String, 
  val sessionTimeout: FiniteDuration = 30.seconds, 
  val connectionTimeout: FiniteDuration = 30.seconds
  )(implicit executionContext: ExecutionContext)
  extends Log
{
  lazy val client = new ZkClient(
    connectString, 
    sessionTimeout.toMillis.toInt, 
    connectionTimeout.toMillis.toInt, 
    ZKStringSerializer
  )
  
  def getBrokers: Future[Seq[KafkaBroker]] = {
    future {
      val brokerIds = ZkUtils.getChildrenParentMayNotExist(client, ZkUtils.BrokerIdsPath).map(_.toInt).sorted
      val brokers = (brokerIds map { brokerId =>
        ZkUtils.readDataMaybeNull(client, ZkUtils.BrokerIdsPath + "/" + brokerId)._1 match {
          case Some(s) => Some(KafkaBroker(brokerId,s))
          case None    => None
        }
      }).filter(_.isDefined).map(_.get)
      brokers
    }
  }
  
  def getTopics: Future[Seq[String]] = {
    future {
      ZkUtils.getAllTopics(client).sorted
    }
  }
  
  def getConsumerGroups: Future[Seq[String]] = {
    future {
      ZkUtils.getChildren(client, ZkUtils.ConsumersPath).sorted
    }
  }
  
  def getConsumers(group: String): Future[Seq[String]] = {
    future {
      ZkUtils.getConsumersInGroup(client, group).sorted
    }
  }
}

object Utils {
  // Like Executors.newCachedThreadPool() except limited to 10 threads and 10s instead of 60s lifetime.
  private lazy val threadPool = new ThreadPoolExecutor(0, 10, 10L, TimeUnit.SECONDS, new SynchronousQueue[Runnable])
  private lazy val executionContext = ExecutionContext.fromExecutor(threadPool)
  
  def apply(
    connectString: String, 
    sessionTimeout: FiniteDuration = 30.seconds,
    connectionTimeout: FiniteDuration = 30.seconds
  ): Utils = {
    implicit val context = executionContext
    new Utils(connectString, sessionTimeout, connectionTimeout)
  }
}
