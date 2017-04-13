package com.aviatainc.dslink.jira.model

import play.api.libs.json._
import com.aviatainc.dslink.jira.util.JsonTranscoder

case class JiraQueryResult(
    expand: String,
    startAt: Long,
    maxResults: Int,
    total: Int,
    issues: List[JiraIssue]
)

object JiraQueryResult extends JsonTranscoder[JiraQueryResult] {
  override implicit val writes = Json.writes[JiraQueryResult]
  override implicit val reads = Json.reads[JiraQueryResult]
}