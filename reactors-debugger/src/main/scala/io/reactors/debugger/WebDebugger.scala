package io.reactors
package debugger



import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.atomic._
import org.json4s._
import org.json4s.JsonDSL._
import scala.collection._
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext.Implicits.global



class WebDebugger(val system: ReactorSystem)
extends DebugApi with Protocol.Service with WebApi {
  private val expirationSeconds = 240
  private val expirationCheckSeconds = 150
  private val server: WebServer = new WebServer(system, this)
  private val monitor = system.monitor
  private val startTime = System.currentTimeMillis()
  private val uidCount = new AtomicLong
  private var lastActivityTime = System.currentTimeMillis()
  private val replManager = new ReplManager(system)
  @volatile private var deltaDebugger: DeltaDebugger = null
  @volatile private var breakpointDebugger: BreakpointDebugger = null

  {
    // Check for expiration every once in a while.
    system.globalTimer.schedule(new TimerTask {
      def run() = checkExpired()
    }, expirationCheckSeconds * 1000)
  }

  /* internal api */

  private def uniqueId(): String = {
    UUID.randomUUID().toString + ":" + uidCount.getAndIncrement() + ":" + startTime
  }

  def isEnabled = deltaDebugger != null

  def eventSent[@spec(Int, Long, Double) T](c: Channel[T], x: T) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) {
        deltaDebugger.eventSent(c, x)
        breakpointDebugger.eventSent(c, x)
      }
    }
  }

  def eventDelivered[@spec(Int, Long, Double) T](c: Channel[T], x: T) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) {
        deltaDebugger.eventDelivered(c, x)
        breakpointDebugger.eventDelivered(c, x)
      }
    }
  }

  def reactorStarted(r: Reactor[_]) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) {
        deltaDebugger.reactorStarted(r)
        breakpointDebugger.reactorStarted(r)
      }
    }
  }

  def reactorScheduled(r: Reactor[_]) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) {
        deltaDebugger.reactorScheduled(r)
        breakpointDebugger.reactorScheduled(r)
      }
    }
  }

  def reactorPreempted(r: Reactor[_]) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) {
        deltaDebugger.reactorPreempted(r)
        breakpointDebugger.reactorPreempted(r)
      }
    }
  }

  def reactorDied(r: Reactor[_]) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) {
        deltaDebugger.reactorDied(r)
        breakpointDebugger.reactorDied(r)
      }
    }
  }

  def reactorTerminated(r: Reactor[_]) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) {
        deltaDebugger.reactorTerminated(r)
        breakpointDebugger.reactorTerminated(r)
      }
    }
  }

  def connectorOpened[T](c: Connector[T]) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) {
        deltaDebugger.connectorOpened(c)
        breakpointDebugger.connectorOpened(c)
      }
    }
  }

  def connectorSealed[T](c: Connector[T]) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) {
        deltaDebugger.connectorSealed(c)
        breakpointDebugger.connectorSealed(c)
      }
    }
  }

  def shutdown() {
    server.shutdown()
  }

  /* external api */

  private def ensureLive() {
    monitor.synchronized {
      if (deltaDebugger == null) {
        deltaDebugger = new DeltaDebugger(system, uniqueId())
        breakpointDebugger = new BreakpointDebugger(system, deltaDebugger)
      }
      lastActivityTime = System.currentTimeMillis()
    }
  }

  private def checkExpired() {
    monitor.synchronized {
      val now = System.currentTimeMillis()
      if (algebra.time.diff(now, lastActivityTime) > expirationSeconds * 1000) {
        deltaDebugger = null
        breakpointDebugger = null
        monitor.notifyAll()
      }
    }
  }

  def state(suid: String, ts: Long): JValue =
    monitor.synchronized {
      ensureLive()
      deltaDebugger.state(suid, ts)
    }

  private def validateRepl(suid: String, repluid: Long, tpe: String) = {
    monitor.synchronized {
      if (suid == deltaDebugger.sessionuid) replManager.repl(repluid, tpe)
      else replManager.repl(-1, tpe)
    }
  }

  def replGet(suid: String, repluid: Long, tpe: String): JValue =
    monitor.synchronized {
      val uidRepl = validateRepl(suid, repluid, tpe)
      val response = uidRepl match {
        case Some((nrepluid, repl)) => (
          ("suid" -> deltaDebugger.sessionuid) ~
          ("repluid" -> nrepluid)
        )
        case None =>
          ("error" -> s"REPL type '${tpe}' is unknown.") ~ ("" -> (null: String))
      }
      response
    }

  def replEval(suid: String, repluid: Long, cmd: String): Future[JValue] =
    monitor.synchronized {
      validateRepl(suid, repluid, "") match {
        case None =>
          Future.successful(JObject("error" -> JString("REPL session expired.")))
        case Some((nrepluid, repl)) =>
          repl.eval(cmd).map(_.asJson)
      }
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
    system.names.resolve
  }
}
