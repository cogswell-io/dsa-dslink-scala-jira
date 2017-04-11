package com.aviatainc.dslink.jira.util

import java.util.concurrent.CompletableFuture
import scala.concurrent.Promise
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executor
import java.util.function.BiConsumer
import com.google.common.util.concurrent.ListenableFuture

object Futures {
  private def runnable(action: => Unit): Runnable = new Runnable {
    override def run(): Unit = action
  }
  
  private def executor(ec: ExecutionContext): Executor = new Executor {
    override def execute(runnable: Runnable): Unit = ec.execute(runnable)
  }
  
  private def biConsumer[A, B](
      consumeA: (A) => Unit,
      consumeB: (B) => Unit
  ): BiConsumer[A, B] = new BiConsumer[A, B] {
    override def accept(a: A, b: B): Unit = {
      if (a != null) consumeA(a)
      if (b != null) consumeB(b)
    }
  }

  /**
   * Convert a CompletableFuture into a Scala Future.
   * 
   * @param future the future to convert
   * 
   * @return the converted future
   */
  def convert[T](future: CompletableFuture[T])(implicit ec: ExecutionContext): Future[T] = {
    val promise: Promise[T] = Promise[T]

    future.whenCompleteAsync(biConsumer(
        { value: T => promise.success(value) },
        { error: Throwable => promise.failure(error) }
    ), executor(ec))
    
    promise.future
  }
  
  /**
   * Convert a ListenableFuture into a Scala Future.
   * 
   * @param future the future to convert
   * 
   * @return the converted future
   */
  def convert[T](future: ListenableFuture[T])(implicit ec: ExecutionContext): Future[T] = {
    val promise: Promise[T] = Promise[T]
    
    future.addListener(runnable {
      if (future.isCancelled()) {
        promise.failure(new RuntimeException("Execution cancelled."))
      } else {
        try {
          val result = future.get()
          promise.success(result)
        } catch {
          case e: Throwable => promise.failure(e)
        }
      }
    }, executor(ec))
    
    promise.future
  }
}