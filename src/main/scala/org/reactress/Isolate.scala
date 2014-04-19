package org.reactress






trait Isolate[@spec(Int, Long, Double) T] {
  def bind(r: Reactive[T]): Reactive.Subscription
}


object Isolate {

}
