package com.aviatainc.dslink.jira.model

import play.api.libs.json._
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import com.aviatainc.dslink.jira.exceptions.JsonValidationException
import com.aviatainc.dslink.jira.util.JsonUtils

/**
 * Metadata stashed with a pub/sub connection node.
 * 
 * @param readKey optional read (subscribe) key for pub/sub
 * @param writeKey optional write (publish) key for pub/sub
 * @param url optional URL, which overrides the default URL
 */
case class JiraConnectionMetadata(
    readKey: Option[String],
    writeKey: Option[String],
    url: Option[String]
) {
  def toJson: JsValue = JiraConnectionMetadata.toJson(this)
}

object JiraConnectionMetadata {
  implicit val writer = Json.writes[JiraConnectionMetadata]
  implicit val reader = Json.reads[JiraConnectionMetadata]
  
  def toJson(metadata: JiraConnectionMetadata): JsValue = Json toJson metadata
  
  def fromJson(json: JsValue): Try[JiraConnectionMetadata] = {
    Json.fromJson[JiraConnectionMetadata](json) match {
      case JsSuccess(metadata, _) => Success(metadata)
      case e: JsError => Failure(new JsonValidationException(e))
    }
  }
  
  def parse(jsonText: String): Try[JiraConnectionMetadata] = {
    JsonUtils.parse(jsonText) flatMap (fromJson(_))
  }
}