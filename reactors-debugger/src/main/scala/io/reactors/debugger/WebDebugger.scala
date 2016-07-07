package io.reactors
package debugger






class WebDebugger(val system: ReactorSystem)
extends DebugApi with Protocol.Service {
  def isEnabled = false

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

  def connectorCreated[T](c: Connector[T]) {
  }

  def connectorSealed[T](c: Connector[T]) {
  }

  def shutdown() {
  }
}
