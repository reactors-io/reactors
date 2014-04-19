package org.reactress



import scala.concurrent.ExecutionContext



package object isolate {

  lazy val globalExecutionContext: Scheduler = new SyncedScheduler.Executor(ExecutionContext.Implicits.global)
  lazy val default: Scheduler = new SyncedScheduler.Executor(new java.util.concurrent.ForkJoinPool)

  object Implicits {
    implicit lazy val globalExecutionContext = isolate.globalExecutionContext
    implicit lazy val default = isolate.default
  }

}
