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
class RCell[@spec(Int, Long, Double) T] private (
  private var value: T,
  private var full: Boolean
) extends Signal[T] {
  private var pushSource: Events.PushSource[T] = _

  private[reactors] def init(dummy: RCell[T]) {
    pushSource = new Events.PushSource[T]
  }

  init(this)

  def this(v: T) = this(v, true)

  /** Returns the current value in the reactive cell.
   */
  def apply(): T = {
    if (isEmpty) throw new NoSuchElementException("empty")
    else value
  }

  /** Clears the cell, making it empty.
   */
  def clear() {
    value = null.asInstanceOf[T]
    full = false
  }

  /** Returns `true` if cell contains no value. */
  def isEmpty = !full

  /** Does nothing. */
  def unsubscribe() {}

  /** Assigns a new value to the reactive cell,
   *  and emits an event with the new value to all the subscribers.
   *
   *  @param v        the new value
   */
  def :=(v: T): Unit = {
    value = v
    full = true
    pushSource.reactAll(v, null)
  }

  /** Same as `:=`. */
  def react(x: T) = this := x

  /** Propagates the exception to all the reactors.
   */
  def except(t: Throwable) = pushSource.exceptAll(t)

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
