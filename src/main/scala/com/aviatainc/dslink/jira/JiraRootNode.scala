package com.aviatainc.dslink.jira

import java.net.URLDecoder
import java.util.concurrent.TimeUnit

import scala.collection.JavaConversions.mapAsScalaMap
import scala.collection.JavaConversions.mutableMapAsJavaMap
import scala.collection.mutable.{ Map => MutableMap }
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Failure
import scala.util.Success

import org.dsa.iot.dslink.DSLink
import org.dsa.iot.dslink.node.Node
import org.dsa.iot.dslink.node.NodeManager
import org.dsa.iot.dslink.node.value.Value
import org.dsa.iot.dslink.node.value.ValueType
import org.slf4j.LoggerFactory

import io.cogswell.dslink.pubsub.model.ActionNodeName
import io.cogswell.dslink.pubsub.model.ConnectionNodeName
import io.cogswell.dslink.pubsub.model.LinkNodeName
import io.cogswell.dslink.pubsub.model.NameKey
import com.aviatainc.dslink.jira.model.JiraConnectionMetadata
import io.cogswell.dslink.pubsub.services.CogsPubSubService
import com.aviatainc.dslink.jira.util.ActionParam
import com.aviatainc.dslink.jira.util.LinkUtils
import com.aviatainc.dslink.jira.util.StringyException

case class JiraRootNode() extends LinkNode {
  private val logger = LoggerFactory.getLogger(getClass)
  
  private val connections = MutableMap[NameKey, JiraConnectionNode]()
  
  override def linkReady(link: DSLink)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("Initializing the root node.")
    
    val manager: NodeManager = link.getNodeManager
    val rootNode = manager.getSuperRoot
    
    // Connect action
    val NAME_PARAM = "name"
    val URL_PARAM = "url"
    val READ_KEY_PARAM = "read"
    val WRITE_KEY_PARAM = "write"
    
    def addConnection(
        name: ConnectionNodeName,
        metadata: Option[JiraConnectionMetadata]
    ): Future[Unit] = {
      val connection = JiraConnectionNode(rootNode, name, metadata)
      connections(name.key) = connection
      
      connection.linkReady(link) andThen {
        case Success(_) => logger.info("Connected to the Pub/Sub service.")
        case Failure(error) => {
          logger.error("Error connecting to the Pub/Sub service:", error)
          connection.destroy()
        }
      }
    }
    
    val connectAction = LinkUtils.action(Seq(
        ActionParam(NAME_PARAM, ValueType.STRING),
        ActionParam(URL_PARAM, ValueType.STRING, Some(new Value(CogsPubSubService.defaultUrl))),
        ActionParam(READ_KEY_PARAM, ValueType.STRING, Some(new Value(""))),
        ActionParam(WRITE_KEY_PARAM, ValueType.STRING, Some(new Value("")))
    )) { actionData =>
      val map = actionData.dataMap
      
      val alias = map(NAME_PARAM).value.map(_.getString).getOrElse("")
      val url = map(URL_PARAM).value.map(_.getString).filter(!_.isEmpty)
      val readKey = map(READ_KEY_PARAM).value.map(_.getString).filter(!_.isEmpty)
      val writeKey = map(WRITE_KEY_PARAM).value.map(_.getString).filter(!_.isEmpty)
      val name = ConnectionNodeName(alias)
      
      logger.info(s"Add Connection action invoked.")
      logger.debug(s"'${URL_PARAM}' : ${url}")
      logger.debug(s"'${READ_KEY_PARAM}' : ${readKey}")
      logger.debug(s"'${WRITE_KEY_PARAM}' : ${writeKey}")
      logger.debug(s"'${NAME_PARAM}' : ${alias}")
      
      if (connections.keys.toSeq.contains(name)) {
        throw new IllegalArgumentException(s"Name $alias already in use.")
      }
      
      if (url.isEmpty) {
        throw new IllegalArgumentException("Url must be provided.")
      }
      
      if (readKey.isEmpty && writeKey.isEmpty) {
        throw new IllegalArgumentException("At least one key must be provided.");
      }
      
      Await.result(
        addConnection(
            name, Some(JiraConnectionMetadata(readKey, writeKey, url))
        ) transform({v => v}, {e => new StringyException(e)}),
        Duration(30, TimeUnit.SECONDS)
      )
      
      logger.info("Connection node should now exist")
    }
    
    LinkUtils.getOrMakeNode(
        rootNode, ActionNodeName("add-connection", "Add Connection")
    ) setAction connectAction
    
    // Synchronize connection nodes with map of nodes.
    {
      val nodeKeys = LinkUtils.getNodeChildren(rootNode)
      .keySet map { nodeId =>
        logger.debug(s"Assembling name for node '$nodeId'")
        (nodeId, LinkNodeName.fromNodeId(nodeId))
      } map {
        // We are only interested in connection nodes
        case (_, Some(name: ConnectionNodeName)) => {
          logger.info(s"Connection node found: $name")
          Some(name.key)
        }
        case (_, Some(name)) => {
          logger.info(s"Non-connection node found: $name")
          None
        }
        case (nodeId, _) => {
          logger.warn(s"Could not determine type for node '$nodeId'!")
          None
        }
      } filter { _.isDefined } map { _.get }
      
      (nodeKeys ++ connections.keySet) map { key =>
        (key, nodeKeys.contains(key), connections.containsKey(key))
      } foreach {
        case (key, false, true) => {
          logger.info(s"Removing node found in map, but not in link: ${key.name}")
          connections.remove(key) foreach { _.destroy() }
        }
        case (key, true, false) => {
          logger.info(s"Adding node found in link, but not in map: ${key.name}")
          key.name match {
            case connectionName: ConnectionNodeName => addConnection(connectionName, None)
            case _ => logger.warn(s"Whoops! Attempting to add non-connection node: ${key.name}")
          }
        }
        case (key, true, true) => {
          logger.info(s"Re-initializing node on link ready: ${key.name}")
          connections(key).linkReady(link)
        }
        case (key, false, false) => logger.warn(s"Node not found in link or map: ${key.name}")
      }
    }
    
    Future.successful()
  }
}