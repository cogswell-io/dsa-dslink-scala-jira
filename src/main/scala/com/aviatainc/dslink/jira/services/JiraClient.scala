package com.aviatainc.dslink.jira.services

import scala.concurrent.Future

import scala.concurrent.ExecutionContext
import java.util.UUID
import io.cogswell.dslink.pubsub.model.PubSubMessage
import io.cogswell.dslink.pubsub.subscriber.PubSubSubscriber
import play.api.libs.ws.WS
import play.api.libs.ws.ning.NingWSClient
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.ws.ahc.AhcWSAPI
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.WSAuthScheme
import play.api.libs.ws.WSResponse
import play.api.libs.json._

sealed trait JiraIdentifier
case class JiraId(val id: Long) extends JiraIdentifier
case class JiraKey(val key: String) extends JiraIdentifier

/**
 * The definition of a new JIRA issue.
 */
case class NewJiraIssue(
    project: JiraIdentifier,
    summary: String,
    description: String,
    issueType: String
)

/**
 * Represents a JIRA issue.
 */
case class JiraIssue(
    project: JiraIdentifier,
    issue: JiraIdentifier,
    summary: String,
    description: String
)

/**
 * Represents a JIRA query.
 */
case class JiraQuery(
    project: JiraIdentifier,
    query: String
)

object JiraClient {
  private implicit lazy val system = ActorSystem()
  private implicit lazy val materializer = ActorMaterializer()
  
  val JIRA_BASE_URL = "https://aviatainc.atlassian.net/rest/api/2"
}

/**
 * An HTTP client for interacting with the JIRA REST API.
 */
case class JiraClient(
    username: String,
    password: String
)(implicit ec: ExecutionContext) {
  private implicit val system = JiraClient.system
  private implicit val materializer = JiraClient.materializer
  private lazy val wsClient = AhcWSClient()
  
  private implicit val writesProjectIdentifier = new Writes[JiraIdentifier] {
    override def write(identifier: JiraIdentifier): JsValue = identifier match {
      case JiraId(id) => Json.obj("id" -> id)
      case JiraKey(key) => Json.obj("key" -> key)
    }
  }
  private implicit val writesNewJiraIssue = Json.writes[NewJiraIssue]

  /**
   * Clean up resources allocated for interaction with JIRA.
   */
  def disconnect()(implicit ec: ExecutionContext): Future[Unit] = {
    wsClient.close()
    Future.successful(())
  }

  /**
   * Creates a new JIRA issue.
   */
  def createIssue(issue: NewJiraIssue)(implicit ec: ExecutionContext): Future[Unit] = {
    wsClient
    .url(s"${JiraClient.JIRA_BASE_URL}/issue/")
    .withAuth(username, password, WSAuthScheme.BASIC)
    .withHeaders(
        ("Content-Type" -> "application/json")
    )
    .withBody(Json.toJson(issue))
    .post map { result =>
      
      ()
    }
  }
  
  /**
   * Searches for JIRA issues.
   */
  def findIssues(query: JiraQuery)(implicit ec: ExecutionContext): Future[Seq[JiraIssue]] = {
    
  }
}