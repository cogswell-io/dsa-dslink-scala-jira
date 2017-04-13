package com.aviatainc.dslink.jira.services

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import com.aviatainc.dslink.jira.model.JiraIdentifier
import com.aviatainc.dslink.jira.model.JiraQueryResult
import com.aviatainc.dslink.jira.model.NewJiraIssue

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.ByteString
import play.api.libs.ws.WSAuthScheme
import play.api.libs.ws.ahc.AhcWSClient

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
    .post(ByteString(issue.toJson.toString))
    .map(_ => ())
  }
  
  /**
   * Searches for JIRA issues.
   */
  def findIssues(query: JiraQuery)(implicit ec: ExecutionContext): Future[JiraQueryResult] = {
    wsClient
    .url(s"${JiraClient.JIRA_BASE_URL}/search?jql=${query}")
    .withAuth(username, password, WSAuthScheme.BASIC)
    .withHeaders(
        ("Content-Type" -> "application/json")
    )
    .get()
    .collect {
      case result if result.status == 200 => result
    }
    .flatMap { result =>
      JiraQueryResult.parse(result.body) match {
        case Success(results) => Future.successful(results)
        case Failure(cause) => Future.failed(cause)
      }
    }
  }
}