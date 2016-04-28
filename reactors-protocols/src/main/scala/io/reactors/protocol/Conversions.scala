package io.reactors
package protocol






/** Utilities that convert values of different types to events streams.
 */
trait Conversions {
  /** Methods that convert collections to event streams.
   *
   *  Since standard collections are not specialized, boxing is potentially possible.
   */
  implicit class TraversableOps[T](val xs: Traversable[T]) {
    def toEvents: Events[T] = new Conversions.ToEvents(xs)
  }
}


object Conversions {
  private[protocol] class ToEvents[T](val xs: Traversable[T]) extends Events[T] {
    def onReaction(obs: Observer[T]): Subscription = {
      try {
        for (x <- xs) obs.react(x, null)
      } catch {
        case NonLethal(t) => obs.except(t)
      } finally {
        obs.unreact()
      }
      Subscription.empty
    }
  }
}
