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

import io.cogswell.dslink.pubsub.model.PubSubOptions
import com.aviatainc.dslink.jira.model.JiraClientMetadata
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
import com.aviatainc.dslink.jira.services.JiraClient
import com.aviatainc.dslink.jira.model.JiraKey
import com.aviatainc.dslink.jira.model.JiraId
import com.aviatainc.dslink.jira.services.JiraQuery
import com.aviatainc.dslink.jira.model.JiraIdentifier
import com.aviatainc.dslink.jira.model.NewJiraIssue

case class JiraClientNode(
    parentNode: Node,
    clientName: ConnectionNodeName,
    metadata: Option[JiraClientMetadata] = None
)(implicit ec: ExecutionContext) extends LinkNode {
  private var client: Option[JiraClient] = None
  private var clientNode: Option[Node] = None
  
  private val logger = LoggerFactory.getLogger(getClass)

  private val METADATA_NODE_NAME = InfoNodeName("metadata", "Metadata")

  private lazy val clientMetadata: Option[JiraClientMetadata] = {
    metadata orElse {
      logger.info(s"Metadata is not populated. Fetching from node ${clientName}.")
      
      getMetadata()
    }
  }
  
  private def getMetadata(): Option[JiraClientMetadata] = {
    logger.debug(s"Fetching metadata for connection ${clientName}")
    
    clientNode flatMap {
      LinkUtils.getChildNode(_, METADATA_NODE_NAME)
    } flatMap { child =>
      Option(child.getValue().getString)
    } flatMap { json =>
      logger.debug(s"Metadata for the connection: $json")
      
      JiraClientMetadata.parse(json) match {
        case Success(md) => {
          logger.debug(s"Fetched metadata for connection ${clientName}: $md")
          Some(md)
        }
        case Failure(error) => {
          logger.error(s"Failed to parse metadata for connection ${clientName}:", error)
          None
        }
      }
    }
  }
  
  private def setMetadata(metadata: JiraClientMetadata): Unit = {
    logger.debug(s"Setting metadata for connection ${clientName}: $metadata")
    
    clientNode foreach { node =>
      LinkUtils.getOrMakeNode(node, METADATA_NODE_NAME, Some { builder =>
        builder.setValueType(ValueType.STRING)
      })
      .setValue(new Value(metadata.toJson.toString))
    }
  }

  override def linkReady(link: DSLink)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info(s"Initializing client ${clientName}")
    
    val PROJECT_ID_PARAM = "project-id"
    val PROJECT_KEY_PARAM = "project-key"
    val SUMMARY_PARAM = "summary"
    val DESCRIPTION_PARAM = "description"
    val QUERY_PARAM = "query"
    
    // Client node
    clientNode = Option(LinkUtils.getOrMakeNode(parentNode, clientName))
    
    clientMetadata foreach { metadata =>
      logger.info(s"Writing connection metadata: $clientMetadata")
      setMetadata(metadata)
    }
    
    clientNode foreach { cNode =>
      // Remove client action node
      LinkUtils.getOrMakeNode(cNode, ActionNodeName("remove-client", "Remove Client"))
      .setAction(LinkUtils.action(Seq()) { actionData =>
        logger.info(s"Removing client '$clientName'")
        destroy()
      })
      
      def projectIdentifier(map: Map[String, ActionParam]): Option[JiraIdentifier] = {
        map(PROJECT_ID_PARAM).value.map { id =>
          JiraId(id.getNumber.longValue)
        } orElse {
          map(PROJECT_KEY_PARAM).value.map { key =>
            JiraKey(key.getString)
          }
        }
      }
        
      // Subscribe action node
      LinkUtils.getOrMakeNode(cNode, ActionNodeName("create-issue", "Create Issue"))
      .setAction(LinkUtils.action(Seq(
          ActionParam(PROJECT_ID_PARAM, ValueType.NUMBER),
          ActionParam(PROJECT_KEY_PARAM, ValueType.STRING),
          ActionParam(SUMMARY_PARAM, ValueType.STRING),
          ActionParam(DESCRIPTION_PARAM, ValueType.STRING)
      )) { actionData =>
        val map = actionData.dataMap
        
        projectIdentifier(map) flatMap { identifier =>
          (
            map(SUMMARY_PARAM).value.map(_.getString),
            map(DESCRIPTION_PARAM).value.map(_.getString)
          ) match {
            case (Some(summary), Some(description)) => Some(identifier, summary, description)
            case _ => None
          }
        } match {
          case Some((identifier, summary, description)) => client map {
            _.createIssue(NewJiraIssue(identifier, summary, description, "Task"))
          }
          case None => {
            val message = "Missing a required parameter."
            logger.warn(message)
            throw new IllegalArgumentException(message)
          }
        }
      })
      
      // Publisher action node
      LinkUtils.getOrMakeNode(cNode, ActionNodeName("find-issues", "Find Issues"))
      .setAction(LinkUtils.action(Seq(
          ActionParam(PROJECT_ID_PARAM, ValueType.NUMBER),
          ActionParam(PROJECT_KEY_PARAM, ValueType.STRING),
          ActionParam(QUERY_PARAM, ValueType.STRING)
      )) { actionData =>
        val map = actionData.dataMap

        projectIdentifier(map) flatMap { identifier =>
          map(QUERY_PARAM).value map { query =>
            (identifier, query.getString)
          }
        } match {
          case Some((identifier, query)) => client.map(c => c.findIssues(JiraQuery(identifier, query)))
          case None => {
            val message = "Missing a required parameter."
            logger.warn(message)
            throw new IllegalArgumentException(message)
          }
        }
      })
    }
    
    Future.successful(())
  }
  
  def destroy(): Unit = {
    client.foreach(_.disconnect())
    clientNode.foreach(parentNode.removeChild(_))
  }
  
  def connect()(implicit ec: ExecutionContext): Future[Unit] = {
    val keys = clientMetadata.fold(Seq.empty[String]) { m =>
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