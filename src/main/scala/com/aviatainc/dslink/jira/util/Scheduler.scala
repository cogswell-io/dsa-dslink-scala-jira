package com.aviatainc.dslink.jira.util

import org.dsa.iot.dslink.util.Objects
import scala.concurrent.duration.Duration

/**
 * Utility for scheduling future actions.
 */
object Scheduler {
  private def pool = Objects.getDaemonThreadPool
  private def runnable(action: => Unit) = new Runnable {
    def run(): Unit = action
  }
  
  /**
   * Schedule an action to be performed after some delay.
   * 
   * @param delay the amount of time to wait before the action invocation
   * @param action the action to perform
   */
  def schedule(delay: Duration)(action: => Unit): Unit = {
    pool.schedule(runnable(action), delay.length, delay.unit)
  }
  
  /**
   * Schedule an action to be performed repeatedly, with a consistent
   * delay between invocations.
   * 
   * @param interval the amount of time to wait between action invocations
   * @param initialDelay an optional delay before the first invocation (defaults to no wait)
   * @param action the action to perform repeatedly
   */
  def repeat(
      interval: Duration,
      initialDelay: Duration = Duration.Zero
  )(action: => Unit): Unit = {
    def doAgain: Unit = {
      action
      schedule(interval)(doAgain)
    }
    
    schedule(initialDelay)(doAgain)
  }
}