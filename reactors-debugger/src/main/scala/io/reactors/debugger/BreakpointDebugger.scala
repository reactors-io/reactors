package io.reactors
package debugger



import io.reactors.common.UnrolledRing



class BreakpointDebugger(val system: ReactorSystem, val deltaDebugger: DeltaDebugger)
extends DebugApi {
  private val monitor = system.monitor

  def isEnabled = true

  def eventSent[@spec(Int, Long, Double) T](c: Channel[T], x: T) {
  }

  def eventDelivered[@spec(Int, Long, Double) T](c: Channel[T], x: T) {
  }

  def reactorStarted(r: Reactor[_]) {
  }

  def reactorScheduled(r: Reactor[_]) {
  }

  def reactorPreempted(r: Reactor[_]) {
  }

  def reactorDied(r: Reactor[_]) {
  }

  def reactorTerminated(r: Reactor[_]) {
  }

  def connectorOpened[T](c: Connector[T]) {
  }

  def connectorSealed[T](c: Connector[T]) {
  }
}
