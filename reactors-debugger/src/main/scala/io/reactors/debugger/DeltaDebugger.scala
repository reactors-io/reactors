package io.reactors
package debugger



import io.reactors.common.UnrolledRing
import org.json4s._
import org.json4s.JsonDSL._
import scala.collection._



class DeltaDebugger(val system: ReactorSystem, val sessionuid: String)
extends DebugApi {
  private val monitor = system.monitor
  private val windowSize = 128
  private val oldstate = new DeltaDebugger.State()
  private var oldtimestamp = 0L
  private val curstate = oldstate.copy
  private var curtimestamp = 0L
  private val deltas = new UnrolledRing[DeltaDebugger.Delta]

  private def enqueue(delta: DeltaDebugger.Delta) {
    monitor.synchronized {
      deltas.enqueue(delta)
      delta.apply(curstate)
      curtimestamp += 1
      if (deltas.size > windowSize) {
        val oldestdelta = deltas.dequeue()
        oldestdelta.apply(oldstate)
        oldtimestamp += 1
      }
    }
  }

  def state(suid: String, reqts: Long): DeltaDebugger.Update = {
    monitor.synchronized {
      if (suid != sessionuid || reqts < oldtimestamp) {
        DeltaDebugger.Update(curtimestamp, sessionuid, Some(curstate.copy()), None)
      } else {
        val newdeltas = mutable.Buffer[DeltaDebugger.Delta]()
        var ts = oldtimestamp
        for (delta <- deltas) {
          if (ts > reqts) newdeltas += delta
          ts += 1
        }
        DeltaDebugger.Update(curtimestamp, sessionuid, None, Some(newdeltas))
      }
    }
  }

  def isEnabled = true

  def eventSent[@spec(Int, Long, Double) T](c: Channel[T], x: T) {}

  def eventDelivered[@spec(Int, Long, Double) T](c: Channel[T], x: T) {}

  def reactorStarted(r: Reactor[_]) = enqueue(DeltaDebugger.ReactorStarted(r))

  def reactorScheduled(r: Reactor[_]) {}

  def reactorPreempted(r: Reactor[_]) {}

  def reactorDied(r: Reactor[_]) = enqueue(DeltaDebugger.ReactorDied(r))

  def reactorTerminated(r: Reactor[_]) = enqueue(DeltaDebugger.ReactorTerminated(r))

  def connectorOpened[T](c: Connector[T]) = enqueue(DeltaDebugger.ConnectorOpened(c))

  def connectorSealed[T](c: Connector[T]) = enqueue(DeltaDebugger.ConnectorSealed(c))
}


object DeltaDebugger {
  case class Update(
    ts: Long, suid: String, state: Option[State], deltas: Option[Seq[Delta]]
  ) {
    def toJson = (
      ("ts" -> ts) ~
      ("suid" -> suid) ~
      ("state" -> state.map(_.toJson)) ~
      ("deltas" -> deltas.map(_.map(_.toJson)))
    )
  }

  class State() {
    def copy(): State = {
      new State()
    }
    def toJson: JValue = JObject()
  }

  abstract class Delta {
    def toJson: JValue
    def apply(s: State): Unit
  }

  case class ReactorStarted(r: Reactor[_]) extends Delta {
    def toJson = JArray(List("start", r.uid, r.frame.name))
    def apply(s: State) {
    }
  }

  case class ReactorDied(r: Reactor[_]) extends Delta {
    def toJson = JArray(List("die", r.uid))
    def apply(s: State) {
    }
  }

  case class ReactorTerminated(r: Reactor[_]) extends Delta {
    def toJson = JArray(List("term", r.uid))
    def apply(s: State) {
    }
  }

  case class ConnectorOpened(c: Connector[_]) extends Delta {
    def toJson = JArray(List("open", c.uid))
    def apply(s: State) {
    }
  }

  case class ConnectorSealed(c: Connector[_]) extends Delta {
    def toJson = JArray(List("seal", c.uid))
    def apply(s: State) {
    }
  }
}
