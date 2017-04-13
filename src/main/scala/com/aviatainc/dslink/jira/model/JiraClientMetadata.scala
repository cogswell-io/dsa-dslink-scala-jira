package com.aviatainc.dslink.jira.model

import play.api.libs.json._
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import com.aviatainc.dslink.jira.exceptions.JsonValidationException
import com.aviatainc.dslink.jira.util.JsonUtils
import com.aviatainc.dslink.jira.util.JsonTranscoder

/**
 * Metadata stashed with a JIRA client node.
 */
case class JiraClientMetadata(
    username: String,
    password: String
) {
  def toJson: JsValue = JiraClientMetadata.toJson(this)
}

object JiraClientMetadata extends JsonTranscoder[JiraClientMetadata] {
  override implicit val writes = Json.writes[JiraClientMetadata]
  override implicit val reads = Json.reads[JiraClientMetadata]
}