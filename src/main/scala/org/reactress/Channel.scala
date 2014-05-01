package org.reactress



import scala.collection._



trait Channel[@spec(Int, Long, Double) T] {
  def attach(r: Reactive[T]): Channel[T]
  def seal(): Channel[T]
}


object Channel {

  class Synced[@spec(Int, Long, Double) T](val reactor: Reactor[T], val monitor: util.Monitor)
  extends Channel[T] {
    private var sealedChannel = false
    private val reactives = mutable.Map[Reactive[T], Reactive.Subscription]()
    def attach(r: Reactive[T]) = monitor.synchronized {
      if (!sealedChannel) {
        if (!reactives.contains(r)) reactives(r) = r.onReaction(new Reactor[T] {
          def react(event: T) = reactor.react(event)
          def unreact() {
            monitor.synchronized { reactives.remove(r) }
            checkTerminated()
          }
        })
      }
      this
    }
    def seal(): Channel[T] = {
      monitor.synchronized { sealedChannel = true }
      checkTerminated()
      this
    }
    private[reactress] def checkTerminated() {
      val done = monitor.synchronized { sealedChannel && reactives.isEmpty }
      if (done) reactor.unreact()
    }
  }

}
