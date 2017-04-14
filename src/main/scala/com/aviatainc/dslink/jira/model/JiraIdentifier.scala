package com.aviatainc.dslink.jira.model

import play.api.libs.json._
import com.aviatainc.dslink.jira.util.JsonTranscoder

sealed trait JiraIdentifier {
  def toJson: JsValue = JiraIdentifier toJson this
}
case class JiraId(val id: Long) extends JiraIdentifier
case class JiraKey(val key: String) extends JiraIdentifier

object JiraId extends JsonTranscoder[JiraId] {
  override implicit val writes = Json.writes[JiraId]
  override implicit val reads = Json.reads[JiraId]
}

object JiraKey extends JsonTranscoder[JiraKey] {
  override implicit val writes = Json.writes[JiraKey]
  override implicit val reads = Json.reads[JiraKey]
}

object JiraIdentifier extends JsonTranscoder[JiraIdentifier] {
  override implicit val writes = new Writes[JiraIdentifier] {
    override def writes(identifier: JiraIdentifier): JsValue = identifier match {
      case JiraId(id) => Json.obj("id" -> id)
      case JiraKey(key) => Json.obj("key" -> key)
    }
  }

  override implicit val reads = new Reads[JiraIdentifier] {
    override def reads(json: JsValue): JsResult[JiraIdentifier] = {
      (json \ "id").toOption.collect[JiraIdentifier] {
        case id:JsNumber => JiraId(id.value.longValue)
      } orElse {
        (json \ "key").toOption.collect[JiraIdentifier] {
          case key:JsString => JiraKey(key.value.toString)
        }
      } match {
        case Some(identifier) => JsSuccess(identifier)
        case None => JsError(Seq())
      }
    }
  }
}