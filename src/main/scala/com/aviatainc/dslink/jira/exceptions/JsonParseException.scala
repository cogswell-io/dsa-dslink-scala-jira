package com.aviatainc.dslink.jira.exceptions

/**
 * Indicates an error parsing text as JSON.
 * 
 * @param message a message regarding the failure
 * @param cause an optional Exception which is the cause of the parse failure
 */
case class JsonParseException(
    message: String, 
    cause: Option[Throwable] = None
) extends Exception(message, cause.getOrElse(null))