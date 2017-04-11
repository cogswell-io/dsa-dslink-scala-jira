package com.aviatainc.dslink.jira.exceptions

import play.api.libs.json.JsError

/**
 * Indicates a failure validating a JsValue as a defined type.
 * 
 * @param error the JsError indicating what was wrong with the structure
 */
case class JsonValidationException(
  error: JsError
) extends Exception("") {
  override def getMessage(): String = {
    JsError toJson error toString
  }
}