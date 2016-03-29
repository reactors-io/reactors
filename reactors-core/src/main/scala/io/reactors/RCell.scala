package io.reactors



import scala.reflect.ClassTag



/** The reactive cell abstraction represents a mutable memory location
 *  whose changes may produce events.
 *
 *  An `RCell` is conceptually similar to a reactive emitter lifted into a signal.
 *
 *  @tparam T         the type of the values it stores
 *  @param value      the initial value of the reactive cell
 */
class RCell[@spec(Int, Long, Double) T](private var value: T)
extends Signal[T] with Observer[T] {
  private var pushSource: Events.PushSource[T] = _

  private[reactors] def init(dummy: RCell[T]) {
    pushSource = new Events.PushSource[T]
  }

  init(this)

  /** Returns the current value in the reactive cell.
   */
  def apply(): T = value

  /** Returns `false`. */
  def isEmpty = false

  /** Does nothing. */
  def unsubscribe() {}

  /** Assigns a new value to the reactive cell,
   *  and emits an event with the new value to all the subscribers.
   *
   *  @param v        the new value
   */
  def :=(v: T): Unit = {
    value = v
    pushSource.reactAll(v, null)
  }

  /** Same as `:=`. */
  def react(x: T) = this := x

  def react(x: T, hint: Any) = react(x)

  /** Propagates the exception to all the reactors.
   */
  def except(t: Throwable) = pushSource.exceptAll(t)

  /** Does nothing -- a cell never unreacts.
   */
  def unreact() {}

  def onReaction(obs: Observer[T]): Subscription = {
    obs.react(value, null)
    pushSource.onReaction(obs)
  }

  override def toString = s"RCell($value)"

}


object RCell {

  /** A factory method for creating reactive cells.
   */
  def apply[@spec(Int, Long, Double) T](x: T) = new RCell[T](x)
  
}
