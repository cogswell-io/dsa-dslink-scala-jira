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

import com.aviatainc.dslink.jira.model.JiraClientMetadata
import com.aviatainc.dslink.jira.util.ActionParam
import com.aviatainc.dslink.jira.util.LinkUtils
import com.aviatainc.dslink.jira.util.StringyException
import com.aviatainc.dslink.jira.model.ClientNodeName
import com.aviatainc.dslink.jira.model.NameKey
import com.aviatainc.dslink.jira.model.ActionNodeName
import com.aviatainc.dslink.jira.model.LinkNodeName

case class JiraRootNode() extends LinkNode {
  private val logger = LoggerFactory.getLogger(getClass)
  
  private val clients = MutableMap[NameKey, JiraClientNode]()
  
  override def linkReady(link: DSLink)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("Initializing the root node.")
    
    val manager: NodeManager = link.getNodeManager
    val rootNode = manager.getSuperRoot
    
    // Connect action
    val USERNAME_PARAM = "username"
    val PASSWORD_PARAM = "password"
    val ORG_PARAM = "organization"
    val URL_PARAM = "base-url"
    
    def addClient(
        name: ClientNodeName,
        metadata: Option[JiraClientMetadata]
    ): Future[Unit] = {
      val connection = JiraClientNode(rootNode, name, metadata)
      clients(name.key) = connection
      
      connection.linkReady(link) andThen {
        case Success(_) => logger.info("Connected to the Pub/Sub service.")
        case Failure(error) => {
          logger.error("Error connecting to the Pub/Sub service:", error)
          connection.destroy()
        }
      }
    }
    
    val addClientAction = LinkUtils.action(Seq(
        ActionParam(USERNAME_PARAM, ValueType.STRING),
        ActionParam(PASSWORD_PARAM, ValueType.STRING),
        ActionParam(ORG_PARAM, ValueType.STRING, Some(new Value(""))),
        ActionParam(URL_PARAM, ValueType.STRING, Some(new Value("")))
    )) { actionData =>
      val map = actionData.dataMap
      
      val username = stringParam(map)(USERNAME_PARAM).getOrElse("")
      val password = stringParam(map)(PASSWORD_PARAM).getOrElse("")
      val org = stringParam(map)(ORG_PARAM).filter(!_.isEmpty)
      val url = stringParam(map)(URL_PARAM).filter(!_.isEmpty)
      
      if (username.isEmpty) {
        throw new IllegalArgumentException("Username must be provided.")
      }
      
      if (password.isEmpty) {
        throw new IllegalArgumentException("Password must be provided.");
      }
      
      Await.result(
        addClient(
            ClientNodeName(username), Some(JiraClientMetadata(username, password, org, url))
        ) transform({v => v}, {e => new StringyException(e)}),
        Duration(30, TimeUnit.SECONDS)
      )
      
      logger.info("Client node should now exist")
    }
    
    LinkUtils.getOrMakeNode(
        rootNode, ActionNodeName("add-client", "Add Client")
    ) setAction addClientAction
    
    // Synchronize connection nodes with map of nodes.
    {
      val nodeKeys = LinkUtils.getNodeChildren(rootNode)
      .keySet map { nodeId =>
        logger.debug(s"Assembling name for node '$nodeId'")
        (nodeId, LinkNodeName.fromNodeId(nodeId))
      } map {
        // We are only interested in connection nodes
        case (_, Some(name: ClientNodeName)) => {
          logger.info(s"client node found: $name")
          Some(name.key)
        }
        case (_, Some(name)) => {
          logger.info(s"Non-client node found: $name")
          None
        }
        case (nodeId, _) => {
          logger.warn(s"Could not determine type for node '$nodeId'!")
          None
        }
      } filter { _.isDefined } map { _.get }
      
      (nodeKeys ++ clients.keySet) map { key =>
        (key, nodeKeys.contains(key), clients.containsKey(key))
      } foreach {
        case (key, false, true) => {
          logger.info(s"Removing node found in map, but not in link: ${key.name}")
          clients.remove(key) foreach { _.destroy() }
        }
        case (key, true, false) => {
          logger.info(s"Adding node found in link, but not in map: ${key.name}")
          key.name match {
            case clientName: ClientNodeName => addClient(clientName, None)
            case _ => logger.warn(s"Whoops! Attempting to add non-connection node: ${key.name}")
          }
        }
        case (key, true, true) => {
          logger.info(s"Re-initializing node on link ready: ${key.name}")
          clients(key).linkReady(link)
        }
        case (key, false, false) => logger.warn(s"Node not found in link or map: ${key.name}")
      }
    }
    
    Future.successful()
  }
}