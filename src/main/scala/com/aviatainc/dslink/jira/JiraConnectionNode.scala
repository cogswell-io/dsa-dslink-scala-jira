package com.aviatainc.dslink.jira

import java.net.ConnectException
import java.util.concurrent.TimeUnit

import scala.collection.JavaConversions._
import scala.collection.mutable.{ Map => MutableMap }
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Failure
import scala.util.Success

import org.dsa.iot.dslink.DSLink
import org.dsa.iot.dslink.node.Node
import org.dsa.iot.dslink.node.value.Value
import org.dsa.iot.dslink.node.value.ValueType
import org.slf4j.LoggerFactory

import com.aviatainc.dslink.jira.services.PubSubConnection
import io.cogswell.dslink.pubsub.model.PubSubOptions
import com.aviatainc.dslink.jira.model.JiraConnectionMetadata
import io.cogswell.dslink.pubsub.model.NameKey
import com.aviatainc.dslink.jira.services.Services
import io.cogswell.dslink.pubsub.util.ActionParam
import io.cogswell.dslink.pubsub.util.LinkUtils
import io.cogswell.dslink.pubsub.util.StringyException
import io.cogswell.dslink.pubsub.model.LinkNodeName
import io.cogswell.dslink.pubsub.model.ConnectionNodeName
import io.cogswell.dslink.pubsub.model.InfoNodeName
import io.cogswell.dslink.pubsub.model.ActionNodeName
import io.cogswell.dslink.pubsub.model.PublisherNodeName
import io.cogswell.dslink.pubsub.model.SubscriberNodeName
import org.slf4j.Marker

case class JiraConnectionNode(
    parentNode: Node,
    connectionName: ConnectionNodeName,
    metadata: Option[JiraConnectionMetadata] = None
)(implicit ec: ExecutionContext) extends LinkNode {
  private var connection: Option[JiraConnection] = None
  private var connectionNode: Option[Node] = None
  
  private val logger = LoggerFactory.getLogger(getClass)
  private var statusNode: Option[Node] = None

  private val METADATA_NODE_NAME = InfoNodeName("metadata", "Metadata")

  private def setStatus(status: String): Unit = {
    logger.info(s"Setting status to '$status' for connection '${connectionName.alias}'")
    statusNode.foreach(_.setValue(new Value(status)))
  }
  
  private def closeHandler: (Option[Throwable]) => Unit = { cause =>
    setStatus("Disconnected")
  }

  private def reconnectHandler: () => Unit = { () =>
    setStatus("Connected")
    validateSubscriptions()
  }
  
  /**
   * Confirm that the subscriptions in the pub/sub service match those
   * which we have stored in this connection. If there are subscriptions 
   * missing, re-subscribe to those channels. If there are extraneous
   * subscriptions, un-subscribe from those channels.
   */
  private def validateSubscriptions(): Unit = {
    connection.foreach { conn =>
      conn.subscriptions() map {
        _.map(SubscriberNodeName(_))
      } map { _.toSet } map { subs =>
        // Combine all channels both the pub/sub service and locally
        val keys = subs.map(_.key) ++ subscribers.keys
        
        keys.map { key =>
          // Identify to which a channel belongs (service, local, or both)
           (key, subscribers.contains(key), subs.contains(key))
        } collect {
          case (key, true, false) => {
            // If a channel is local only, re-subscribe to it. 
            subscribers.get(key).foreach { node =>
              node.subscribe() andThen {
                case Success(_) =>
                  logger.info(s"Re-subscribed to channel ${key.name.alias}")
                case Failure(error) =>
                  logger.error(s"Error re-subscribing to channel ${key.name.alias}:", error)
              }
            }
          }
          case (key, false, true) => {
            // If a channel is on the service only, un-subscribe from it.
            key.name match {
              case name: SubscriberNodeName => {
                connection.foreach { conn =>
                  conn.unsubscribe(name.channel) andThen {
                    case Success(_) =>
                      logger.info(s"Successfully un-subsbscribed from channel ${name.channel}")
                    case Failure(error) =>
                      logger.error(s"Error un-subsbscribed from channel ${name.channel}:", error)
                  }
                }
              }
              case _ => logger.warn(s"Node name is of the wrong type: ${key}")
            }
          }
        }
      }
    }
  }
  
  private def restoreSubscribers(link: DSLink): Unit = {
    logger.info(s"Restoring subscribers for connection ${connectionName}")

    // Synchronize subscriber nodes with map of nodes.
    {
      val nodeKeys = connectionNode
      .map(node => LinkUtils.getNodeChildren(node))
      .fold(Set.empty[String])(_.keySet) map {
        LinkNodeName.fromNodeId(_)
      } collect {
        // We are only interested in subscriber nodes
        case Some(subscriberName: SubscriberNodeName) => subscriberName.key
      }
      
      (nodeKeys ++ subscribers.keySet) map { key =>
        (key.name, nodeKeys.contains(key), subscribers.containsKey(key))
      } foreach {
        case (subscriberName: SubscriberNodeName, false, true) =>
          subscribers.remove(subscriberName.key) foreach { _.destroy() }
        case (subscriberName: SubscriberNodeName, true, false) =>
          connectionNode.foreach { addSubscriber(link, _, subscriberName) }
        case (subscriberName: SubscriberNodeName, true, true) =>
          subscribers(subscriberName.key).linkReady(link)
        case t =>
          logger.warn(s"Bad state for subscriber: $t")
      }
    }
  }
  
  private def restorePublishers(link: DSLink): Unit = {
    logger.info(s"Restoring publishers for connection ${connectionName}")

    // Synchronize publisher nodes with map of nodes.
    {
      val nodeKeys = connectionNode
      .map(node => LinkUtils.getNodeChildren(node))
      .fold(Set.empty[String])(_.keySet) map {
        LinkNodeName.fromNodeId(_)
      } collect {
        // We are only interested in publisher nodes
        case Some(publisherName: PublisherNodeName) => publisherName.key
      }

      (nodeKeys ++ subscribers.keySet) map { key =>
        (key.name, nodeKeys.contains(key), subscribers.containsKey(key))
      } foreach {
        case (publisherName: PublisherNodeName, false, true) =>
          subscribers.remove(publisherName.key) foreach { _.destroy() }
        case (publisherName: PublisherNodeName, true, false) =>
          connectionNode.foreach { addPublisher(link, _, publisherName) }
        case (publisherName: PublisherNodeName, true, true) =>
          subscribers(publisherName.key).linkReady(link)
        case t =>
          logger.warn(s"Bad state for subscriber: $t")
      }
    }
  }
  
  private lazy val connectionMetadata: Option[JiraConnectionMetadata] = {
    metadata orElse {
      logger.info(s"Metadata is not populated. Fetching from node ${connectionName}.")
      
      getMetadata()
    }
  }
  
  private lazy val connectionOptions: PubSubOptions = {
    PubSubOptions(
      closeListener = Some(closeHandler),
      reconnectListener = Some(reconnectHandler),
      url = connectionMetadata.flatMap(_.url)
    )
  }
  
  private def getMetadata(): Option[JiraConnectionMetadata] = {
    logger.debug(s"Fetching metadata for connection ${connectionName}")
    
    connectionNode flatMap {
      LinkUtils.getChildNode(_, METADATA_NODE_NAME)
    } flatMap { child =>
      Option(child.getValue().getString)
    } flatMap { json =>
      logger.debug(s"Metadata for the connection: $json")
      
      JiraConnectionMetadata.parse(json) match {
        case Success(md) => {
          logger.debug(s"Fetched metadata for connection ${connectionName}: $md")
          Some(md)
        }
        case Failure(error) => {
          logger.error(s"Failed to parse metadata for connection ${connectionName}:", error)
          None
        }
      }
    }
  }
  
  private def setMetadata(metadata: JiraConnectionMetadata): Unit = {
    logger.debug(s"Setting metadata for connection ${connectionName}: $metadata")
    
    connectionNode foreach { node =>
      LinkUtils.getOrMakeNode(node, METADATA_NODE_NAME, Some { builder =>
        builder.setValueType(ValueType.STRING)
      })
      .setValue(new Value(metadata.toJson.toString))
    }
  }

  override def linkReady(link: DSLink)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info(s"Initializing connection ${connectionName}")
    
    val CHANNEL_PARAM = "channel"
    val MESSAGE_PARAM = "message"
    
    // Connection node
    connectionNode = Option(LinkUtils.getOrMakeNode(parentNode, connectionName))
    
    connectionMetadata foreach { metadata =>
      logger.info(s"Writing connection metadata: $connectionMetadata")
      setMetadata(metadata)
    }
    
    connectionNode foreach { cNode =>
      // Status indicator node
      statusNode = Some(LinkUtils.getOrMakeNode(
          cNode, InfoNodeName("status", "Status"),
          Some { _
            .setValueType(ValueType.STRING)
            .setValue(new Value("Unknown"))
          }
      ))
      
      // Disconnect action node
      LinkUtils.getOrMakeNode(cNode, ActionNodeName("remove-connection", "Remove Connection"))
      .setAction(LinkUtils.action(Seq()) { actionData =>
        logger.info(s"Closing connection '$connectionName'")
        destroy()
      })
      
      // Subscribe action node
      LinkUtils.getOrMakeNode(cNode, ActionNodeName("add-subscriber", "Add Subscriber"))
      .setAction(LinkUtils.action(Seq(
          ActionParam(CHANNEL_PARAM, ValueType.STRING)
      )) { actionData =>
        val map = actionData.dataMap
        
        map(CHANNEL_PARAM).value.map(_.getString) match {
          case Some(channel) => {
            Await.result(
              addSubscriber(link, cNode, SubscriberNodeName(channel)) transform (
                {v => v}, {e => new StringyException(e)}
              ), 
              Duration(30, TimeUnit.SECONDS)
            )
          }
          case None => {
            val message = "No channel supplied for new subscriber."
            logger.warn(message)
            throw new IllegalArgumentException(message)
          }
        }
      })
        
      // Publisher action node
      LinkUtils.getOrMakeNode(cNode, ActionNodeName("add-publisher", "Add Publisher"))
      .setAction(LinkUtils.action(Seq(
          ActionParam(CHANNEL_PARAM, ValueType.STRING)
      )) { actionData =>
        val map = actionData.dataMap
        
        map(CHANNEL_PARAM).value.map(_.getString) match {
          case Some(channel) => addPublisher(link, cNode, PublisherNodeName(channel))
          case None => {
            val message = "No channel supplied for new publisher."
            logger.warn(message)
            throw new IllegalArgumentException(message)
          }
        }
      })
      
      // Publish action
      LinkUtils.getOrMakeNode(cNode, ActionNodeName("publish", "Publish"))
      .setAction(LinkUtils.action(Seq(
          ActionParam(CHANNEL_PARAM, ValueType.STRING),
          ActionParam(MESSAGE_PARAM, ValueType.STRING, Some(new Value("")))
      )) { actionData =>
        val map = actionData.dataMap
        val message = map(MESSAGE_PARAM).value.map(_.getString).getOrElse("")

        map(CHANNEL_PARAM).value.map(_.getString) match {
          case Some(channel) => connection.foreach(_.publish(channel, message))
          case _ => {
            val message = "Missing channel and/or message for publish action."
            logger.warn(message)
            throw new IllegalArgumentException(message)
          }
        }
      })
    }
    
    connection.fold(connect())(_ => Future.successful()) map { _ =>
      restoreSubscribers(link)
      restorePublishers(link)
      
      ()
    }
  }
  
  private def addSubscriber(
      link: DSLink, parentNode: Node, subscriberName: SubscriberNodeName
  ): Future[Unit] = {
    val channel = subscriberName.channel
    logger.info(s"Adding subscriber to channel '${channel}'")
    
    connection match {
      case None => {
        logger.warn("No connection found when attempting to subscribe!")
        Future.failed(new ConnectException("Pub/Sub connection does not exist."))
      }
      case Some(conn) => {
        val subscriber = PubSubSubscriberNode(parentNode, conn, subscriberName)
        
        subscriber.subscribe() flatMap { _ =>
          subscriber.linkReady(link)
        } andThen {
          case Success(_) => {
            logger.info(s"[$connectionName] Succesfully added subscriber to channel '${channel}'")
            subscribers.put(subscriberName.key, subscriber)
          }
          case Failure(error) => {
            logger.error(s"[$connectionName] Error adding subscriber to channel '${channel}':", error)
            subscriber.destroy()
          }
        } map { _ => Unit }
      }
    }
  }
  
  private def addPublisher(
      link: DSLink, parentNode: Node, publisherName: PublisherNodeName
  ): Unit = {
    val channel = publisherName.channel
    logger.info(s"Adding publisher $publisherName")
    
    connection match {
      case None => {
        logger.warn("No connection found when attempting to setup publisher!")
        throw new ConnectException("Pub/Sub connection is offline.")
      }
      case Some(conn) => {
        val publisher = PubSubPublisherNode(parentNode, conn, publisherName)

        publisher.linkReady(link) andThen {
          case Success(_) => {
            logger.info(s"Succesfully added publisher $publisherName")
            publishers.put(publisherName.key, publisher)
          }
          case Failure(error) => {
            logger.error(s"Error adding publisher $publisherName:", error)
            publisher.destroy()
          }
        }
      }
    }
  }
  
  def destroy(): Unit = {
    subscribers.foreach(_._2.destroy())
    publishers.foreach(_._2.destroy())
    connection.foreach(_.disconnect())
    connectionNode.foreach(parentNode.removeChild(_))
  }
  
  def connect()(implicit ec: ExecutionContext): Future[Unit] = {
    val keys = connectionMetadata.fold(Seq.empty[String]) { m =>
      Seq(m.readKey, m.writeKey) collect { case Some(key) => key }
    }
    
    Services.pubSubService.connect(keys, Some(connectionOptions)) map { conn =>
      logger.info("Connected to the pub/sub service.")
      
      setStatus("Connected")
      connection = Some(conn)
      ()
    }
  }
}