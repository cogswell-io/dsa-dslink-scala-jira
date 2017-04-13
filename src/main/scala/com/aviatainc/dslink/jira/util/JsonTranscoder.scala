package com.aviatainc.dslink.jira.util

import play.api.libs.json._
import com.aviatainc.dslink.jira.exceptions.JsonValidationException
import scala.util.Try
import scala.util.Failure
import scala.util.Success

trait JsonTranscoder[T] {
  def writes: Writes[T]
  def reads: Reads[T]
  
  def toJson(record: T): JsValue = Json.toJson(record)(writes)
  
  def fromJson(json: JsValue): Try[T] = {
    Json.fromJson[T](json)(reads) match {
      case JsSuccess(record, _) => Success(record)
      case e: JsError => Failure(new JsonValidationException(e))
    }
  }
  
  def parse(jsonText: String)(implicit reads: Reads[T]): Try[T] = {
    JsonUtils.parse(jsonText) flatMap (fromJson(_))
  }
}