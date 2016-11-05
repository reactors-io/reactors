package io.reactors
package protocol






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

  case class Pump[T](
    buffer: EventBuffer[T],
    subscription: Subscription
  ) {
    def plug(valve: Valve[T])(implicit a: Arrayable[T]): Subscription = {
      ???
//      val pump = this
//
//      pump.buffer onEvent { x =>
//        if (valve.available()) {
//          valve.channel ! x
//          pump.channel ! 1
//        } else {
//          buffer.enqueue(x)
//        }
//      }
//
//      valve.available.filter(_ == true).onEvent { x =>
//        while (valve.available() && buffer.nonEmpty) {
//          valve.channel ! buffer.dequeue()
//          pump.channel ! 1
//        }
//      }
    }
  }
}
