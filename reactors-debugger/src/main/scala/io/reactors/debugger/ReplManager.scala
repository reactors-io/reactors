package io.reactors
package debugger



import java.util.concurrent.atomic.AtomicLong
import scala.collection._
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._



class ReplManager(val system: ReactorSystem) {
  val monitor = system.monitor
  val uidCount = new AtomicLong
  val repls = mutable.Map[Long, ReplManager.Session]()

  def get(uid: Long, tpe: String): Option[Repl] = monitor.synchronized {
    repls.get(uid) match {
      case Some(s) if s.repl.tpe == tpe => Some(s.repl)
      case None => None
    }
  }
}


object ReplManager {
  class Session(val repl: Repl) {
    val creationTimestamp = System.currentTimeMillis()
  }
}
