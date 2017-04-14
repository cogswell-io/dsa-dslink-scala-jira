package com.aviatainc.dslink.jira.model

import com.aviatainc.dslink.jira.util.JsonTranscoder

import play.api.libs.json._
import com.aviatainc.dslink.jira.util.JsonEncoder

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

object NewJiraIssue extends JsonEncoder[NewJiraIssue] {
  override implicit val writes = new Writes[NewJiraIssue] {
    override def writes(issue: NewJiraIssue): JsValue = {
      Json.obj(
        "fields" -> Json.obj(
          "project" -> issue.project.toJson,
          "summary" -> issue.summary,
          "description" -> issue.description,
          "issuetype" -> Json.obj(
            "name" -> issue.issueType
          )
        )
      )
    }
  }
}