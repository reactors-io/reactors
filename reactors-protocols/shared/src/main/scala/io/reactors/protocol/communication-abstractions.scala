package io.reactors
package protocol






trait CommunicationAbstractions {
  case class Link[T](
    channel: Channel[T],
    available: Signal[Boolean],
    subscription: Subscription
  )

  trait Connection[T] {
    def events: Events[T]
    def subscription: Subscription
  }

  trait ServerSide[C] {
     def connections: Events[C]
     def subscription: Subscription
  }
}
