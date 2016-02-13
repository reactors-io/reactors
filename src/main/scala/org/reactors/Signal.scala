package org.reactors



import org.reactors.common._



/** A special type of an event stream that caches the last emitted event.
 *
 *  This last event is called the signal's ''value''.
 *  It can be read using the `Signal`'s `apply` method.
 *  
 *  @tparam T        the type of the events in this signal
 */
trait Signal[@spec(Int, Long, Double) T] extends Events[T] {

  /** Returns the last event produced by `this` signal.
   *
   *  
   *
   *  @return         the signal's value
   *  @throws         `NoSuchElementException` if the signal does not contain an event
   */
  def apply(): T

  /** An event stream that only emits events when the value of `this` signal changes.
   *
   *  {{{
   *  time    --------------->
   *  this    --1---2--2--3-->
   *  changes --1---2-----3-->
   *  }}}
   *
   *  @return         a subscription and the signal with changes of `this`
   */
  def changes: Events[T] = new Signal.Changes(this)

}


object Signal {

  class Changes[@spec(Int, Long, Double) T](val self: Signal[T]) extends Events[T] {
    def onReaction(obs: Observer[T]) =
      self.onReaction(new Signal.ChangesObserver[T](obs, self()))
  }

  class ChangesObserver[@spec(Int, Long, Double) T](
    val target: Observer[T],
    var cached: T
  ) extends Observer[T] {
    def react(x: T) = if (cached != x) {
      cached = x
      target.react(x)
    }
    def except(t: Throwable) = target.except(t)
    def unreact() = target.unreact()
  }

}
