package com.aviatainc.dslink.jira.model

import com.aviatainc.dslink.jira.util.JsonTranscoder
import play.api.libs.json._

case class JiraIssue(
    id: Long,
    key: String,
    self: String,
    data: JsValue
)

object JiraIssue extends JsonTranscoder[JiraIssue] {
  override implicit val writes = Json.writes[JiraIssue]
  override implicit val reads = Json.reads[JiraIssue]
}