package org.reactress



import scala.concurrent.ExecutionContext



package object isolate {

  lazy val globalExecutionContext: Reactor = new Reactor.SyncedExecutor(ExecutionContext.Implicits.global)
  lazy val default: Reactor = new Reactor.SyncedExecutor(new java.util.concurrent.ForkJoinPool)

  object Implicits {
    implicit lazy val globalExecutionContext = isolate.globalExecutionContext
    implicit lazy val default = isolate.default
  }

}
