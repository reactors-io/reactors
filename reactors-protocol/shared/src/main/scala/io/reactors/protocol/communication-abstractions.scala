package io.reactors
package protocol



import io.reactors.container.RHashSet



trait CommunicationAbstractions {
  trait Connection[T] {
    def events: Events[T]
    def subscription: Subscription
  }

  trait ServerSide[R, C] {
    def channel: Channel[R]
    def connections: Events[C]
    def subscription: Subscription
  }

  case class Valve[T](
    channel: Channel[T],
    available: Signal[Boolean],
    subscription: Subscription
  )

  class MultiValve[T] {
    private val valves = new RHashSet[Valve[T]]
    val available: Signal[Boolean] =
      valves.map(_.available).toSignalAggregate(true)(_ && _)
    val channel: Channel[T] = ???
    def +=(x: Valve[T]): Unit = ???
  }

  case class Pump[T](
    buffer: EventBuffer[T],
    subscription: Subscription
  ) {
    def plug(valve: Valve[T])(implicit a: Arrayable[T]): Subscription = {
      val pump = this

      val forwardSub = pump.buffer.onEvent(x => valve.channel ! x)

      def flush(): Unit = {
        while (pump.buffer.nonEmpty && valve.available()) {
          pump.buffer.dequeue()
        }
      }

      val valveSub = valve.available.filter(_ == true) on {
        flush()
      }

      val pumpSub = pump.buffer.available.filter(_ == true) on {
        flush()
      }

      new Subscription.Composite(forwardSub, valveSub, pumpSub)
    }
  }
}
