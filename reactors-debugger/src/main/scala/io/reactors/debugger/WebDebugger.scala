package io.reactors
package debugger



import io.reactors.common.Uid
import io.reactors.concurrent.Frame
import java.util.TimerTask
import org.json4s._
import org.json4s.JsonDSL._
import scala.collection._
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext.Implicits.global



class WebDebugger(val system: ReactorSystem)
extends DebugApi with Protocol.Service with WebApi {
  private val expirationSeconds =
    system.bundle.config.int("debug-api.session.expiration")
  private val expirationCheckSeconds =
    system.bundle.config.int("debug-api.session.expiration-check-period")
  private val server: WebServer = new WebServer(system, this)
  private val monitor = system.monitor
  private val startTime = System.currentTimeMillis()
  private var lastActivityTime = System.currentTimeMillis()
  private val replManager = new ReplManager(system)
  @volatile private var sessionUid: String = null
  @volatile private var deltaDebugger: DeltaDebugger = null
  @volatile private var breakpointDebugger: BreakpointDebugger = null

  {
    // Check for expiration every once in a while.
    system.globalTimer.schedule(new TimerTask {
      def run() = checkExpired()
    }, expirationCheckSeconds * 1000)
  }

  /* internal api */

  private def uniqueId(): String = Uid.string(startTime)

  def isEnabled = sessionUid != null

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

  def reactorStarted(f: Frame) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) {
        deltaDebugger.reactorStarted(f)
        breakpointDebugger.reactorStarted(f)
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

  def log(x: Any) {
    if (deltaDebugger != null) monitor.synchronized {
      if (deltaDebugger != null) {
        replManager.log(x)
      }
    }
  }

  def shutdown() {
    server.shutdown()
  }

  /* external api */

  private def ensureLive() {
    monitor.synchronized {
      if (sessionUid == null) {
        sessionUid = uniqueId()
        deltaDebugger = new DeltaDebugger(system, sessionUid)
        breakpointDebugger = new BreakpointDebugger(system, deltaDebugger)
      }
      lastActivityTime = System.currentTimeMillis()
    }
  }

  private def checkExpired() {
    monitor.synchronized {
      val now = System.currentTimeMillis()
      if (algebra.time.diff(now, lastActivityTime) > expirationSeconds * 1000) {
        sessionUid = null
        deltaDebugger = null
        breakpointDebugger = null
        monitor.notifyAll()
      }
    }
  }

  def state(suid: String, ts: Long, ruids: List[String]): JObject =
    monitor.synchronized {
      ensureLive()
      val replouts = replManager.pendingOutputs(ruids).toMap.mapValues(JString).toList
      JObject(
        ("pending-output" -> JObject(replouts)) :: deltaDebugger.state(suid, ts).obj)
    }

  private def validateSessionUid(suid: String): Option[JObject] = {
    if (sessionUid != suid)
      Some(("error" -> "Invalid session UID.") ~ ("suid" -> sessionUid))
    else None
  }

  def breakpointAdd(suid: String, pattern: String, tpe: String): JObject = {
    monitor.synchronized {
      ensureLive()
      validateSessionUid(suid).getOrElse {
        val bid = breakpointDebugger.breakpointAdd(pattern, tpe)
        ("bid" -> bid)
      }
    }
  }

  def breakpointList(suid: String): JObject = {
    monitor.synchronized {
      ensureLive()
      validateSessionUid(suid).getOrElse {
        val breakpoints = for (b <- breakpointDebugger.breakpointList()) yield {
          ("bid" -> b.bid) ~ ("pattern" -> b.pattern) ~ ("tpe" -> b.tpe)
        }
        ("breakpoints" -> breakpoints)
      }
    }
  }

  def breakpointRemove(suid: String, bid: Long): JObject = {
    monitor.synchronized {
      ensureLive()
      validateSessionUid(suid).getOrElse {
        breakpointDebugger.breakpointRemove(bid) match {
          case Some(b) =>
            ("bid" -> b.bid) ~ ("pattern" -> b.pattern) ~ ("tpe" -> b.tpe)
          case None =>
            ("bid" -> bid) ~ ("error" -> "Cannot remove non-existing breakpoint.")
        }
      }
    }
  }

  def replGet(tpe: String): Future[JValue] =
    monitor.synchronized {
      replManager.createRepl(tpe).map({
        case (repluid, repl) =>
          JObject("repluid" -> JString(repluid))
      }).recover({
        case e: Exception =>
          JObject("error" -> JString(s"REPL type '${tpe}' is unknown."))
      })
    }

  def replEval(repluid: String, cmd: String): Future[JValue] =
    monitor.synchronized {
      replManager.getRepl(repluid).flatMap({
        case (uid, repl) => repl.eval(cmd).map(_.asJson)
      }).recover({
        case t: Throwable => JObject("error" -> JString("REPL session expired."))
      })
    }

  def replClose(repluid: String): Future[JValue] =
    monitor.synchronized {
      replManager.getRepl(repluid).flatMap({
        case (uid, repl) => Future(repl.shutdown()).map(_ => JObject())
      }).recover({
        case t: Throwable => JObject("error" -> JString("REPL session expired."))
      })
    }
}


object WebDebugger {
  def main(args: Array[String]) {
    val config = ReactorSystem.customConfig("""
      debug-api = {
        name = "io.reactors.debugger.WebDebugger"
      }
    """)
    val bundle = new ReactorSystem.Bundle(JvmScheduler.default, config)
    val system = new ReactorSystem("web-debugger", bundle)
    system.names.resolve
  }
}
