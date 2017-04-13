package com.aviatainc.dslink.jira.model

import scala.collection.immutable.StringOps
import com.aviatainc.dslink.jira.util.SetOnce
import org.dsa.iot.dslink.node.Node
import java.net.URLDecoder

/**
 * Parent class of all node names. A way to represent a link node,
 * supplying both and id with a known structure, and a flexible alias.
 */
sealed abstract class LinkNodeName(name: String, prefix: String) {
  val id = s"$prefix:$name"
  val key = NameKey(id) identifying this
  
  def alias: String
  
  override def toString: String = alias
}

/**
 * Identifies a client node.
 */
case class ClientNodeName(
    name: String
) extends LinkNodeName(name, "client") {
  override val alias = name
}

/**
 * Identifies an action node.
 */
case class ActionNodeName(
    name: String, alias: String
) extends LinkNodeName(name, "action")

/**
 * Identifies an info node.
 */
case class InfoNodeName(
    name: String, alias: String
) extends LinkNodeName(name, "info")

case class NameKey(id: String) {
  private val linkNodeName = new SetOnce[LinkNodeName]
  
  def identifying(name: LinkNodeName): NameKey = {
    linkNodeName set name
    this
  }
  
  def name: LinkNodeName = linkNodeName.get match {
    case Some(n) => n
    case None => throw new NoSuchElementException("nodeName called before identifying()")
  }
}

object LinkNodeName {
  /**
   * Identifies the correct type of a node name based on its id.
   * 
   * @param id the id string which will be used to determine the node name
   * @param alias for the node
   * 
   * @return an Option which will contain the node name if it can be identified
   */
  def fromNodeId(id: String, alias: String = null): Option[LinkNodeName] = {
    Option(id) map {
      _.split(":", 2).toList
    } flatMap {
      case (category :: name :: Nil) => Some((category, name))
      case _ => None
    } filter {
      case ("", _) | (_, "") => false
      case _ => true
    } collect {
      case (category, name) => (category, name, Option(alias).filter(!_.isEmpty).getOrElse(name))
    } flatMap {
      case ("action", name, alias) => Some(ActionNodeName(name, alias))
      case ("client", name, alias) => Some(ClientNodeName(name))
      case ("info", name, alias) => Some(InfoNodeName(name, alias))
      case _ => None
    }
  }
  
  /**
   * Identifies the correct type of node name based on the data contained
   * within a DSLink node, assembling it based on the data contained therein.
   * 
   * @param node the DSLink node
   * 
   * @return the link name, typed appropriately for the supplied node
   */
  def fromNode(node: Node): Option[LinkNodeName] = {
    val id = URLDecoder.decode(node.getName, "UTF-8")
    val alias = node.getDisplayName
    fromNodeId(id, alias)
  }
}
