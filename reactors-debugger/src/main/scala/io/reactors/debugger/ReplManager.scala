package io.reactors
package debugger



import java.util.concurrent.atomic.AtomicLong
import scala.collection._
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._



class ReplManager(val system: ReactorSystem) {
  val expirationCheckSeconds = 60
  val expirationSeconds = system.bundle.config.getInt("debug-api.repl.expiration")
  val monitor = system.monitor
  val uidCount = new AtomicLong
  val repls = mutable.Map[Long, ReplManager.Session]()

  {
    system.globalTimer.schedule(new TimerTask {
      def run() = checkExpired()
    }, expirationCheckSeconds * 1000)
  }

  private def checkExpired() {
    monitor.synchronized {
      val now = System.currentTimeMillis()
      var dead = List[Long]()
      for ((id, s) <- repls) {
        if (algebra.time.diff(now, s.lastActivityTime) > expirationSeconds * 1000) {
          s.shutdown()
          dead ::= id
        }
      }
      for (id <- dead) repls.remove(id)
    }
  }

  def get(uid: Long, tpe: String): Option[Repl] = monitor.synchronized {
    repls.get(uid) match {
      case Some(s) if s.repl.tpe == tpe => Some(s.repl)
      case None => None
    }
  }
}


object ReplManager {
  class Session(val repl: Repl) {
    var lastActivityTime = System.currentTimeMillis()
  }
}
