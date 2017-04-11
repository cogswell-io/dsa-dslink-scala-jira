package com.aviatainc.dslink.jira.services

sealed abstract class RunMode(val modeName: String)
case object ProductionMode extends RunMode("production")
case object TestMode extends RunMode("test")

object RunMode {
  def forName(modeName: String): Option[RunMode] = {
    modeName match {
      case ProductionMode.modeName => Some(ProductionMode)
      case TestMode.modeName => Some(TestMode)
      case _ => None
    }
  }
}