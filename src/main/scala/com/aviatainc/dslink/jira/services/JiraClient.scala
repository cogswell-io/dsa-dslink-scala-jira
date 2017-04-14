package com.aviatainc.dslink.jira.services

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import com.aviatainc.dslink.jira.model.JiraIdentifier
import com.aviatainc.dslink.jira.model.JiraQueryResult
import com.aviatainc.dslink.jira.model.NewJiraIssue

import javax.xml.bind.DatatypeConverter
import java.nio.charset.Charset
import scalaj.http.Http
import scala.util.Try
import org.slf4j.LoggerFactory
import play.api.libs.json._

/**
 * Represents a JIRA query.
 */
case class JiraQuery(
    query: String,
    fields: Option[Seq[String]],
    limit: Option[Int],
    offset: Option[Int]
) {
  private def params: Seq[(String, JsValue)] = {
    Seq(
      Some(("jql" -> JsString(query))),
      fields.map(fs => ("fields" -> JsArray(fs.map(JsString(_))))),
      offset.map(("startAt" -> JsNumber(_))),
      limit.map(("maxResults" -> JsNumber(_)))
    ) collect {
      case Some(tuple) => tuple
    }
  }

  def toJson: JsValue = JsObject(params)
}

object JiraClient {
  val UTF_8 = Charset.forName("utf-8")
}

/**
 * An HTTP client for interacting with the JIRA REST API.
 */
case class JiraClient(
    username: String,
    password: String,
    organization: Option[String] = Some("aviatainc"),
    url: Option[String] = None
)(implicit ec: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(getClass)

  type HttpHeader = (String, String)
  
  private def baseUrl = url.orElse(
      organization map { org => s"https://${org}.atlassian.net/rest/api/2" }
  ).get

  private def contentJson: HttpHeader = ("Content-Type", "application/json")
  private def acceptJson: HttpHeader = ("Accept", "application/json")
  private def basicAuth: HttpHeader = {
    val cred = s"$username:$password"
    val credB64 = DatatypeConverter.printBase64Binary(cred.getBytes(JiraClient.UTF_8))
    ("Authorization", s"Basic $credB64")
  }
  
  private def jiraResult(json: String): Try[JiraQueryResult] = JiraQueryResult.parse(json)

  /**
   * Creates a new JIRA issue.
   */
  def createIssue(issue: NewJiraIssue)(implicit ec: ExecutionContext): Future[Either[(Int, String), String]] = {
    Future {
      val url = s"${baseUrl}/issue/"
      val data = issue.toJson.toString
      
      logger.info(s"[JIRA-Create-Issue] POST $url\n$data")
      
      Http(url)
      .headers(Seq(
          basicAuth,
          acceptJson,
          contentJson
      ))
      .postData(data)
      .method("POST")
      .asString
    } map { response =>
      response.code match {
        case 200 => Right(response.body)
        case code => Left((code, response.body))
      }
    } andThen {
      case Success(result) => logger.info(s"[JIRA-Create-Issue] result: $result")
      case Failure(error) => logger.error("[JIRA-Create-Issue] error:", error)
    }
  }
  
  /**
   * Searches for JIRA issues.
   */
  def findIssues(query: JiraQuery)(implicit ec: ExecutionContext): Future[Either[(Int, String), String]] = {
    Future {
      val url = s"${baseUrl}/search"
      val data = query.toJson.toString
      
      logger.info(s"[JIRA-Query] POST $url\n$data")
      
      Http(url)
      .headers(Seq(
          basicAuth,
          acceptJson,
          contentJson
      ))
      .postData(data)
      .method("POST")
      .asString
    } map { response =>
      response.code match {
        case 200 => Right(response.body)
        case code => Left((code, response.body))
      }
    } andThen {
      case Success(result) => logger.info(s"[JIRA-Query] result: $result")
      case Failure(error) => logger.info("[JIRA-Query] error:", error)
    }
  }
}