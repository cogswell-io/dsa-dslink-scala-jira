package io.cogswell.dslink.pubsub.model

import com.aviatainc.dslink.jira.DslinkTest
import com.aviatainc.dslink.jira.model.ActionNodeName
import com.aviatainc.dslink.jira.model.InfoNodeName
import com.aviatainc.dslink.jira.model.ClientNodeName
import com.aviatainc.dslink.jira.DslinkTest
import com.aviatainc.dslink.jira.model.LinkNodeName

class LinkNodeNameTest extends DslinkTest() {
  "LinkNodeName.fromNode" should "correctly identify an action" in {
    LinkNodeName.fromNodeId(null, "alias") should be (None)
    LinkNodeName.fromNodeId("", "alias") should be (None)
    LinkNodeName.fromNodeId("action", "alias") should be (None)
    LinkNodeName.fromNodeId("action:", "alias") should be (None)
    
    LinkNodeName.fromNodeId("action:act", null) should be (Some(ActionNodeName("act", "act")))
    LinkNodeName.fromNodeId("action:act", "") should be (Some(ActionNodeName("act", "act")))
    LinkNodeName.fromNodeId("action:act", "alias") should be (Some(ActionNodeName("act", "alias")))
  }
  
  it should "correctly identify a client" in {
    LinkNodeName.fromNodeId(null, "alias") should be (None)
    LinkNodeName.fromNodeId("", "alias") should be (None)
    LinkNodeName.fromNodeId("client", "alias") should be (None)
    LinkNodeName.fromNodeId("client:", "alias") should be (None)
    
    LinkNodeName.fromNodeId("client:cli", null) should be (Some(ClientNodeName("cli")))
    LinkNodeName.fromNodeId("client:cli", "") should be (Some(ClientNodeName("cli")))
    LinkNodeName.fromNodeId("client:cli", "alias") should be (Some(ClientNodeName("cli")))
  }
  
  it should "correctly identify a info" in {
    LinkNodeName.fromNodeId(null, "alias") should be (None)
    LinkNodeName.fromNodeId("", "alias") should be (None)
    LinkNodeName.fromNodeId("info", "alias") should be (None)
    LinkNodeName.fromNodeId("info:", "alias") should be (None)
    
    LinkNodeName.fromNodeId("info:status", null) should be (Some(InfoNodeName("status", "status")))
    LinkNodeName.fromNodeId("info:status", "") should be (Some(InfoNodeName("status", "status")))
    LinkNodeName.fromNodeId("info:status", "alias") should be (Some(InfoNodeName("status", "alias")))
  }
  
  it should "not mis-identify an unknown id" in {
    LinkNodeName.fromNodeId(null, null) should be (None)
    LinkNodeName.fromNodeId(null, "alias") should be (None)
    LinkNodeName.fromNodeId("", null) should be (None)
    LinkNodeName.fromNodeId("", "alias") should be (None)
    LinkNodeName.fromNodeId(":", null) should be (None)
    LinkNodeName.fromNodeId(":", "alias") should be (None)
    LinkNodeName.fromNodeId(":huh", null) should be (None)
    LinkNodeName.fromNodeId(":huh", "alias") should be (None)
    LinkNodeName.fromNodeId("huh", null) should be (None)
    LinkNodeName.fromNodeId("huh", "alias") should be (None)
    LinkNodeName.fromNodeId("huh:", null) should be (None)
    LinkNodeName.fromNodeId("huh:", "alias") should be (None)
    LinkNodeName.fromNodeId("huh:huh", null) should be (None)
    LinkNodeName.fromNodeId("huh:huh", "alias") should be (None)
  }
  
  "LinkNodeName.id" should "be correctly assembled for each category" in {
    ActionNodeName("act", "Action").id should be ("action:act")
    ClientNodeName("conn").id should be ("client:conn")
    InfoNodeName("status", "alias").id should be ("info:status")
  }
  
  it should "supply a key which is only identified by using its id" in {
    ActionNodeName("act", "alias").key.hashCode should be (ActionNodeName("act", "otro").key.hashCode)
    ActionNodeName("act", "alias").key.equals(ActionNodeName("act", "otro").key) should be (true)
  }
}