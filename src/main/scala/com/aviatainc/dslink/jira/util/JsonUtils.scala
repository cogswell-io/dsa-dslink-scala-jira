package com.aviatainc.dslink.jira.util

import scala.util.Try
import com.aviatainc.dslink.jira.exceptions.JsonParseException
import scala.util.Failure
import play.api.libs.json.Json
import play.api.libs.json.JsValue

object JsonUtils {
  /**
   * Parse JSON text into a JsValue.
   * 
   * @param json the text to parse
   * 
   * @return a Try[JsValue]
   */
  def parse(json: String): Try[JsValue] = {
    Try {
      Json.parse(json)
    } recoverWith {
      case t: Throwable => Failure(JsonParseException("Error parsing JSON text.", Some(t)))
    }
  }
}