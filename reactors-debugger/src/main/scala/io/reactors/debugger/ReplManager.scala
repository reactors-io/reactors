package io.reactors
package debugger



import io.reactors.common.Uid
import io.reactors.debugger.repl.ScalaRepl
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicLong
import scala.collection._
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._



class ReplManager(val system: ReactorSystem) {
  val expirationCheckSeconds = 60
  val expirationSeconds = system.bundle.config.getInt("debug-api.repl.expiration")
  val monitor = system.monitor
  val uidCount = new AtomicLong
  val repls = mutable.Map[String, ReplManager.Session]()
  val replFactory = mutable.Map[String, () => Repl]()

  {
    // Start expiration checker.
    system.globalTimer.schedule(new TimerTask {
      def run() = checkExpired()
    }, expirationCheckSeconds * 1000)

    // Add known repls.
    replFactory("Scala") = () => new ScalaRepl
  }

  private def checkExpired() {
    monitor.synchronized {
      val now = System.currentTimeMillis()
      var dead = List[String]()
      for ((id, s) <- repls) {
        if (algebra.time.diff(now, s.lastActivityTime) > expirationSeconds * 1000) {
          s.repl.shutdown()
          dead ::= id
        }
      }
      for (id <- dead) repls.remove(id)
    }
  }

  def repl(uid: String, tpe: String): Option[(String, Repl)] = monitor.synchronized {
    repls.get(uid) match {
      case Some(s) =>
        Some((uid, s.repl))
      case _ =>
        replFactory.get(tpe) match {
          case Some(f) =>
            val s = new ReplManager.Session(f())
            val nuid = Uid.string()
            repls(nuid) = s
            Some((nuid, s.repl))
          case None =>
            None
        }
    }
  }
}


object ReplManager {
  class Session(val repl: Repl) {
    var lastActivityTime = System.currentTimeMillis()
  }
}
