package io.reactors
package protocol






trait LinkProtocols {
  case class Link[T](
    channel: Channel[T],
    available: Signal[Boolean],
    subscription: Subscription
  )
}
