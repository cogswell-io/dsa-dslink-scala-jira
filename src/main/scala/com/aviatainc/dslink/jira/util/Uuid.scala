package com.aviatainc.dslink.jira.util

import java.util.UUID

object Uuid {
  /**
   * Creates a new UUID composed using the current time.
   * 
   * @return a new UUID
   */
  def now(): UUID = new UUID(System.currentTimeMillis(), System.nanoTime())
}