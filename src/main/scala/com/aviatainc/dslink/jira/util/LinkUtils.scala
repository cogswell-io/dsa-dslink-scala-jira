package com.aviatainc.dslink.jira.util

import org.dsa.iot.dslink.util.handler.Handler
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions._
import org.dsa.iot.dslink.node.actions.Action
import org.dsa.iot.dslink.node.actions.Action
import org.dsa.iot.dslink.node.value.Value
import org.dsa.iot.dslink.node.value.ValueType
import org.dsa.iot.dslink.node.actions.ActionResult
import org.dsa.iot.dslink.node.Permission
import org.dsa.iot.dslink.node.actions.Parameter
import org.dsa.iot.dslink.methods.responses.ListResponse
import org.dsa.iot.dslink.DSLink
import org.dsa.iot.dslink.node.Node
import org.dsa.iot.dslink.methods.requests.ListRequest
import org.dsa.iot.dslink.node.NodeBuilder
import java.net.URLDecoder
import java.net.URLEncoder
import com.aviatainc.dslink.jira.model.LinkNodeName

/**
 * Class representing a parameter attached to an Action.
 * 
 * @param name the name of the parameter
 * @param valueType the type of the value
 * @param value the optional value
 */
case class ActionParam(
    name: String,
    valueType: ValueType,
    value: Option[Value] = None
) {
  def parameter: Parameter = {
    value match {
      case None => new Parameter(name, valueType)
      case Some(v) => new Parameter(name, valueType, v)
    }
  }
}

/**
 * Class representing the data passed to an Action when
 * it is invoked.
 */
case class ActionData(
    params: Seq[ActionParam],
    result: ActionResult
) {
  val dataMap: Map[String, ActionParam] = params.map {
    case ActionParam(name, valueType, value) =>
      val resultValue = Option(result.getParameter(name, valueType)).orElse(value)
      (name -> ActionParam(name, valueType, resultValue))
  } toMap
}

object LinkUtils {
  /**
   * Creates a new Handler wrapped around the supplied action,
   * and logs any un-handled Exceptions thrown by the action.
   * 
   * @param action the action which will be invoked by the Handler
   * 
   * @return the new Handler
   */
  def handler[T](action: (T) => Unit): Handler[T] = new Handler[T] {
    private val logger = LoggerFactory.getLogger(action.getClass)
    
    def handle(value: T): Unit = {
      try {
        action(value)
      } catch {
        case error: Throwable => {
          logger.error("Error in Handler:", error)
          throw error
        }
      }
    }
  }
  
  /**
   * Simplifies the creation of a new Action with parameters.
   * 
   * @param params the parameters which should be attached to this action
   * @param permission 
   */
  def action(
      params: Seq[ActionParam],
      permission: Permission = Permission.READ
  )(action: (ActionData) => Unit): Action = {
    val actionHandler = handler { actionResult: ActionResult =>
      action(ActionData(params, actionResult))
    }
    
    val linkAction = new Action(permission, actionHandler)
    
    params.foreach(p => linkAction.addParameter(p.parameter))
    
    linkAction
  }
  
  /**
   * Listen for updates to the supplied node, and call the supplied
   * function with the updates.
   * 
   * @param link the DSLink
   * @param node the Node to which we should listen for updates
   * @param listener the listener to invoke for each update
   */
  def listen(link: DSLink, node: Node)(listener: (ListResponse) => Unit): Unit = {
    val listRequest = new ListRequest(node.getPath)
    val listHandler = new Handler[ListResponse] {
      override def handle(response: ListResponse) {
        listener(response)
      }
    }

    link.getRequester.list(listRequest, listHandler)
  }
  
  /**
   * Fetch the child of another node or create it if it doesn't exist.
   * 
   * @param parent the node to which the child should be attached
   * @param name the name (id and alias) of the child node
   * @param initializer an optional mutator for the builder after it has been
   * created, but before it has been built
   * 
   * @return the child Node
   */
  def getOrMakeNode(
      parent: Node,
      name: LinkNodeName, 
      initializer: Option[(NodeBuilder) => Unit] = None
  ): Node = {
    getChildNode(parent, name) getOrElse {
      val child = parent createChild name.id setDisplayName name.alias
      initializer foreach (_(child))
      child build
    }
  }

  /**
   * Node names are URL encoded, so they need to have this encoding applied
   * before they are used to perform a lookup on a parent node. This function
   * performs the appropriate encoding.
   * 
   * @param nodeName the decoded node name
   * 
   * @return the encoded node name
   */
  def encodeNodeName(nodeName: String): String = URLEncoder.encode(nodeName, "UTF-8")
  
  /**
   * Node names are URL encoded, so they need to be decoded before we perform any
   * manipulation based on the name's original content. This function performs
   * the appropriate decoding to restore the name to its original form.
   * 
   * @param nodeName the possibly encoded node name
   * 
   * @return the decoded node name
   */
  def decodeNodeName(nodeName: String): String = URLDecoder.decode(nodeName, "UTF-8")
  
  /**
   * Fetches the name from a node and decodes it.
   * 
   * @param the node whose name should be fetched
   * 
   * @return the decoded node name
   */
  def getNodeName(node: Node): String = decodeNodeName(node.getName)
  
  /**
   * Fetches child nodes from a node, decodes their names, and  translates
   * to a native Scala map.
   */
  def getNodeChildren(node: Node): Map[String, Node] = {
    Option(node.getChildren())
    .fold(Map.empty[String, Node])(_.toMap)
    .map(pair => (decodeNodeName(pair._1), pair._2))
  }
  
  /**
   * Fetches a child node from a node, encoding the id of specified
   * name before performing the lookup.
   */
  def getChildNode(node: Node, childName: LinkNodeName): Option[Node] = {
    getChildNode(node, childName.id)
  }

  /**
   * Fetches a child node from a node, encoding the specified id before
   * performing the lookup.
   */
  def getChildNode(node: Node, childId: String): Option[Node] = {
    Option(node.getChild(encodeNodeName(childId)))
  }
}