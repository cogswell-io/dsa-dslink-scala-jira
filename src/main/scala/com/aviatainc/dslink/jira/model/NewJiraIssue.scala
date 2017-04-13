package com.aviatainc.dslink.jira.model

import com.aviatainc.dslink.jira.util.JsonTranscoder

import play.api.libs.json._

/**
 * The definition of a new JIRA issue.
 */
case class NewJiraIssue(
    project: JiraIdentifier,
    summary: String,
    description: String,
    issueType: String
) {
  def toJson: JsValue = NewJiraIssue toJson this
}

object NewJiraIssue extends JsonTranscoder[NewJiraIssue] {
  override implicit val writes = Json.writes[NewJiraIssue]
  override implicit val reads = Json.reads[NewJiraIssue]
}