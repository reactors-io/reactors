package io.reactors
package debugger



import java.util.UUID
import java.util.concurrent.atomic._
import scala.collection._



class WebDebugger(val system: ReactorSystem)
extends DebugApi with Protocol.Service with WebApi {
  private val server: WebServer = new WebServer(system, this)
  private val monitor = system.monitor
  private val startTime = System.currentTimeMillis()
  private val uidCount = new AtomicInteger
  @volatile private var deltaDebugger: DeltaDebugger = null
  @volatile private var breakpointDebugger: BreakpointDebugger = null

  /* internal api */

  private def uniqueId(): String = {
    UUID.randomUUID().toString + ":" + uidCount.getAndIncrement() + ":" + startTime
  }

  def isEnabled = deltaDebugger != null

  def eventSent[@spec(Int, Long, Double) T](c: Channel[T], x: T) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) deltaDebugger.eventSent(c, x)
      breakpointDebugger.eventSent(c, x)
    }
  }

  def eventDelivered[@spec(Int, Long, Double) T](c: Channel[T], x: T) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) deltaDebugger.eventDelivered(c, x)
      breakpointDebugger.eventDelivered(c, x)
    }
  }

  def reactorStarted(r: Reactor[_]) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) deltaDebugger.reactorStarted(r)
      breakpointDebugger.reactorStarted(r)
    }
  }

  def reactorScheduled(r: Reactor[_]) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) deltaDebugger.reactorScheduled(r)
      breakpointDebugger.reactorScheduled(r)
    }
  }

  def reactorPreempted(r: Reactor[_]) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) deltaDebugger.reactorPreempted(r)
      breakpointDebugger.reactorPreempted(r)
    }
  }

  def reactorDied(r: Reactor[_]) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) deltaDebugger.reactorDied(r)
      breakpointDebugger.reactorDied(r)
    }
  }

  def reactorTerminated(r: Reactor[_]) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) deltaDebugger.reactorTerminated(r)
      breakpointDebugger.reactorTerminated(r)
    }
  }

  def connectorOpened[T](c: Connector[T]) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) deltaDebugger.connectorOpened(c)
      breakpointDebugger.connectorOpened(c)
    }
  }

  def connectorSealed[T](c: Connector[T]) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) deltaDebugger.connectorSealed(c)
      breakpointDebugger.connectorSealed(c)
    }
  }

  def shutdown() {
    server.shutdown()
  }

  /* external api */

  private def ensureLive() {
    if (deltaDebugger == null) monitor.synchronized {
      if (deltaDebugger == null) {
        deltaDebugger = new DeltaDebugger(system, uniqueId())
        breakpointDebugger = new BreakpointDebugger(system)
      }
    }
  }

  def state(suid: String, ts: Long): WebApi.Update = {
    ensureLive()
    ???
  }
}


object WebDebugger {
  def main(args: Array[String]) {
    val config = ReactorSystem.customConfig("""
      debug-api = {
        name = "io.reactors.debugger.WebDebugger"
      }
    """)
    val bundle = new ReactorSystem.Bundle(Scheduler.default, config)
    val system = new ReactorSystem("web-debugger", bundle)
  }
}
