package com.aviatainc.dslink.jira

import scala.concurrent.ExecutionContext.Implicits.global

import org.dsa.iot.dslink.DSLink
import org.dsa.iot.dslink.DSLinkFactory
import org.dsa.iot.dslink.DSLinkHandler
import org.dsa.iot.dslink.node.Node
import org.dsa.iot.dslink.node.value.Value
import org.slf4j.LoggerFactory

object Main extends DSLinkHandler {
  private val logger = LoggerFactory.getLogger(getClass)

  private var rootNode = JiraRootNode()

  override val isResponder = true

// General handlers
  
  // Handle a failed set operation
  override def onSetFail(path: String, value: Value): Unit = {
    logger.warn(s"Failed to set path '${path}' to value ${value}")
  }
  
  // Handle a failed subscription operation
  override def onSubscriptionFail(path: String): Node = {
    logger.warn(s"Subscription failed to path '${path}'")
    null
  }
  
  // Handle a failed invocation operation
  override def onInvocationFail(path: String): Node = {
    logger.warn(s"Invocation failed for path '${path}'")
    null
  }
  
// Responder handlers
  
  // Handle connection of the Responder (happens after initialization)
  override def onResponderConnected(link: DSLink): Unit = {
    logger.info(s"Responder for path '${link.getPath}' connected")
    rootNode.linkConnected(link)
  }
  
  // Handle initialization of the Responder. This is called after onResponderConnected()!!
  override def onResponderInitialized(link: DSLink): Unit = {
    logger.info(s"Responder for path '${link.getPath}' has been initialized")
    rootNode.linkReady(link)
  }
  
  // Handle disconnection of the Responder
  override def onResponderDisconnected(link: DSLink): Unit = {
    logger.info(s"Responder for path '${link.getPath}' disconnected")
    rootNode.linkDisconnected(link)
  }
  
  // Bootstrap the DSLink
  def main(args: Array[String]): Unit = {
    DSLinkFactory.start(args, Main)
  }
}