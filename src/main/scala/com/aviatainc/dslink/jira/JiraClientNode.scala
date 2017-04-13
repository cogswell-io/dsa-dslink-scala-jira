package com.aviatainc.dslink.jira

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import org.dsa.iot.dslink.DSLink
import org.dsa.iot.dslink.node.Node
import org.dsa.iot.dslink.node.value.Value
import org.dsa.iot.dslink.node.value.ValueType
import org.slf4j.LoggerFactory

import com.aviatainc.dslink.jira.model.ActionNodeName
import com.aviatainc.dslink.jira.model.ClientNodeName
import com.aviatainc.dslink.jira.model.InfoNodeName
import com.aviatainc.dslink.jira.model.JiraClientMetadata
import com.aviatainc.dslink.jira.model.JiraId
import com.aviatainc.dslink.jira.model.JiraIdentifier
import com.aviatainc.dslink.jira.model.JiraKey
import com.aviatainc.dslink.jira.model.NewJiraIssue
import com.aviatainc.dslink.jira.services.JiraClient
import com.aviatainc.dslink.jira.services.JiraQuery
import com.aviatainc.dslink.jira.util.ActionParam
import com.aviatainc.dslink.jira.util.LinkUtils

case class JiraClientNode(
    parentNode: Node,
    clientName: ClientNodeName,
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
    logger.debug(s"Fetching metadata for client ${clientName}")
    
    clientNode flatMap {
      LinkUtils.getChildNode(_, METADATA_NODE_NAME)
    } flatMap { child =>
      Option(child.getValue().getString)
    } flatMap { json =>
      logger.debug(s"Metadata for the client: $json")
      
      JiraClientMetadata.parse(json) match {
        case Success(md) => {
          logger.debug(s"Fetched metadata for client ${clientName}: $md")
          Some(md)
        }
        case Failure(error) => {
          logger.error(s"Failed to parse metadata for client ${clientName}:", error)
          None
        }
      }
    }
  }
  
  private def setMetadata(metadata: JiraClientMetadata): Unit = {
    logger.debug(s"Setting metadata for client ${clientName}: $metadata")
    
    clientNode foreach { node =>
      LinkUtils.getOrMakeNode(node, METADATA_NODE_NAME, Some { builder =>
        builder.setValueType(ValueType.STRING)
      })
      .setValue(new Value(metadata.toJson.toString))
    }
  }

  override def linkReady(link: DSLink)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info(s"Initializing client ${clientName}")
    
    val PROJECT_KEY_PARAM = "project-key"
    val SUMMARY_PARAM = "summary"
    val DESCRIPTION_PARAM = "description"
    val QUERY_PARAM = "query"
    
    // Client node
    clientNode = Option(LinkUtils.getOrMakeNode(parentNode, clientName))
    
    clientMetadata foreach { metadata =>
      logger.info(s"Writing client metadata: $clientMetadata")
      setMetadata(metadata)
    }
    
    clientNode foreach { cNode =>
      // Remove client action node
      LinkUtils.getOrMakeNode(cNode, ActionNodeName("remove-client", "Remove Client"))
      .setAction(LinkUtils.action(Seq()) { actionData =>
        logger.info(s"Removing client '$clientName'")
        destroy()
      })
      
      // Subscribe action node
      LinkUtils.getOrMakeNode(cNode, ActionNodeName("create-issue", "Create Issue"))
      .setAction(LinkUtils.action(Seq(
          ActionParam(PROJECT_KEY_PARAM, ValueType.STRING, Some(new Value(""))),
          ActionParam(SUMMARY_PARAM, ValueType.STRING),
          ActionParam(DESCRIPTION_PARAM, ValueType.STRING)
      )) { actionData =>
        val map = actionData.dataMap
        
        jiraKeyParam(map)(PROJECT_KEY_PARAM) flatMap { key =>
          (
            stringParam(map)(SUMMARY_PARAM),
            stringParam(map)(DESCRIPTION_PARAM)
          ) match {
            case (Some(summary), Some(description)) => Some(key, summary, description)
            case _ => None
          }
        } match {
          case Some((key, summary, description)) => client map {
            _.createIssue(NewJiraIssue(key, summary, description, "Task"))
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
          ActionParam(PROJECT_KEY_PARAM, ValueType.STRING, Some(new Value(""))),
          ActionParam(QUERY_PARAM, ValueType.STRING)
      )) { actionData =>
        val map = actionData.dataMap

        jiraKeyParam(map)(PROJECT_KEY_PARAM) flatMap { key =>
          stringParam(map)(QUERY_PARAM) map { query =>
            (key, query)
          }
        } match {
          case Some((key, query)) => client.map(c => c.findIssues(JiraQuery(key, query)))
          case None => {
            val message = "Missing a required parameter."
            logger.warn(message)
            throw new IllegalArgumentException(message)
          }
        }
      })
    }
    
    client = clientMetadata.map { md =>
      JiraClient(
          md.username, md.password,
          md.organization, md.url
      )
    }
    
    Future.successful(())
  }
  
  def destroy(): Unit = {
    clientNode.foreach(parentNode.removeChild(_))
  }
}