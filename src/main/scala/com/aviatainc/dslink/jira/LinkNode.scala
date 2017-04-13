package com.aviatainc.dslink.jira

import org.dsa.iot.dslink.DSLink
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.aviatainc.dslink.jira.model.JiraKey
import com.aviatainc.dslink.jira.util.ActionParam

trait LinkNode {
  /**
   * Invoked when the link has been connected.
   */
  def linkConnected(link: DSLink)(implicit ec: ExecutionContext): Future[Unit] = {
    Future successful Unit
  }
  
  /**
   * Invoked when the link is ready, so nodes can been queried and created 
   * only after this is called. This actually happens after the connection
   * is established, and therefore is called after linkConnected().
   */
  def linkReady(link: DSLink)(implicit ec: ExecutionContext): Future[Unit]
  
  /**
   * Invoked when the link has been disconnected.
   */
  def linkDisconnected(link: DSLink)(implicit ec: ExecutionContext): Future[Unit] = {
    Future successful Unit
  }
  
  /**
   * Extract a String from a parameter map if it exists.
   */
  protected def stringParam(map: Map[String, ActionParam])(param: String): Option[String] = {
    map(param).value.map(_.getString)
  }
  
  /**
   * Extract a JiraKey from a parameter map if it exists.
   */
  protected def jiraKeyParam(map: Map[String, ActionParam])(param: String): Option[JiraKey] = {
    stringParam(map)(param).map(JiraKey(_))
  }
}