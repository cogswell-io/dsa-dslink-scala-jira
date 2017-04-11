package com.aviatainc.dslink.jira.util

import org.joda.time.DateTime
import java.time.Instant

/**
 * Utilities for manipulating time records.
 */
object TimeUtils {
  /**
   * Converts an Instant to a DateTime.
   * 
   * @param instant the Instant to convert
   * 
   * @return the new DateTime
   */
  def instantToDateTime(instant: Instant): DateTime = {
    new DateTime(instant.toEpochMilli())
  }
}