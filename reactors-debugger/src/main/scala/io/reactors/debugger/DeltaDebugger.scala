package io.reactors
package debugger



import io.reactors.common.UnrolledRing
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._



class DeltaDebugger(val system: ReactorSystem, val sessionUid: String)
extends DebugApi {
  private val monitor = system.monitor
  private val windowSize = 128
  private val oldstate = new DeltaDebugger.State()
  private var oldtimestamp = 0L
  private val curstate = oldstate.copy
  private var curtimestamp = 0L
  private val deltas = new UnrolledRing[DeltaDebugger.Delta]

  private def enqueue(delta: DeltaDebugger.Delta) {
    deltas.enqueue(delta)
    delta.apply(curstate)
    curtimestamp += 1
    if (deltas.size > windowSize) {
      val oldestdelta = deltas.dequeue()
      oldestdelta.apply(oldstate)
      oldtimestamp += 1
    }
  }

  def isEnabled = true

  def eventSent[@spec(Int, Long, Double) T](c: Channel[T], x: T) {
  }

  def eventDelivered[@spec(Int, Long, Double) T](c: Channel[T], x: T) {
  }

  def reactorStarted(r: Reactor[_]) = enqueue(DeltaDebugger.ReactorStarted(r))

  def reactorScheduled(r: Reactor[_]) {
  }

  def reactorPreempted(r: Reactor[_]) {
  }

  def reactorDied(r: Reactor[_]) = enqueue(DeltaDebugger.ReactorDied(r))

  def reactorTerminated(r: Reactor[_]) = enqueue(DeltaDebugger.ReactorTerminated(r))

  def connectorOpened[T](c: Connector[T]) = enqueue(DeltaDebugger.ConnectorOpened(c))

  def connectorSealed[T](c: Connector[T]) = enqueue(DeltaDebugger.ConnectorSealed(c))
}


object DeltaDebugger {
  class State() {
    def copy(): State = {
      new State()
    }
    def asJson: JValue = JObject()
  }

  abstract class Delta {
    def asJson: JValue
    def apply(s: State): Unit
  }

  case class ReactorStarted(r: Reactor[_]) extends Delta {
    def asJson = JArray(List("start", r.uid, r.frame.name))
    def apply(s: State) {
    }
  }

  case class ReactorDied(r: Reactor[_]) extends Delta {
    def asJson = JArray(List("die", r.uid))
    def apply(s: State) {
    }
  }

  case class ReactorTerminated(r: Reactor[_]) extends Delta {
    def asJson = JArray(List("term", r.uid))
    def apply(s: State) {
    }
  }

  case class ConnectorOpened(c: Connector[_]) extends Delta {
    def asJson = JArray(List("open", c.uid))
    def apply(s: State) {
    }
  }

  case class ConnectorSealed(c: Connector[_]) extends Delta {
    def asJson = JArray(List("seal", c.uid))
    def apply(s: State) {
    }
  }
}
