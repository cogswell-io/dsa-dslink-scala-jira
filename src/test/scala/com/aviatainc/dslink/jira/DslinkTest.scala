package com.aviatainc.dslink.jira

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.{ global => globalEC }
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Nanoseconds
import org.scalatest.time.Span
import scaldi.Injector
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.annotation.implicitNotFound

/**
 * Base class for all DSLink tests. The patience is tunable via the
 * timeout and interval parameters.
 * 
 * @param timeout the max duration to wait for a test to complete
 * @param interval the tick frequency for checking whether a test should timeout
 */
abstract class DslinkTest(
    timeout: Duration = Duration(10, TimeUnit.SECONDS),
    interval: Duration = Duration(100, TimeUnit.MILLISECONDS)
) extends FlatSpec with Matchers with ScalaFutures {
  implicit val ec: ExecutionContext = globalEC

  /**
   * Provides the default patience. If you wish to adjust the
   * patience of your test, you can either override this function,
   * or you can adjust the timeout and interval parameters in the
   * constructor (preferred).
   */
  implicit def patience = PatienceConfig(
      timeout = Span(timeout.toNanos, Nanoseconds),
      interval = Span(interval.toNanos, Nanoseconds)
   )
}