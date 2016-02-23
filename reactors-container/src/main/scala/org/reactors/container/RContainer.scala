package org.reactors
package container



import scala.annotation.implicitNotFound



/** Base class for reactive containers.
 *
 *  Reactive container is a collection of elements that exposes event streams that
 *  emit events about incremental changes to the underlying containers.
 *
 *  Every reactive container has output event streams `inserts` and `removes`. When an
 *  element is inserted or removed, these streams emit events. A container may also have
 *  an input stream, which is not exposed, but may be unsubscribed from by calling
 *  `unsubscribe`. Finally, a container has a `size` and `foreach` methods. These basic
 *  primitives are used to implement all other methods.
 *
 *  Other methods return event streams that generally emit the initial value after being
 *  subscribed to. It is therefore legal to call `toEmptySignal` on such event streams
 *  and query them.
 */
trait RContainer[@spec(Int, Long, Double) T] extends Subscription {
  self =>

  /** Event stream with inserted elements.
   */
  def inserts: Events[T]

  /** Event stream with removed elements. 
   */
  def removes: Events[T]

  /** Unsubscribes the container from its input event streams.
   */
  def unsubscribe(): Unit

  /** Traverses the elements of the container.
   */
  def foreach(f: T => Unit): Unit

  /** Returns the number of elements in the container.
   */
  def size: Int

  /** Stream with the current number of elements satisfying a predicate.
   */
  def count(p: T => Boolean): Events[Int] = new RContainer.Count(this, p)

  /** Stream with boolean values indicating if all elements satisfy a predicate.
   */
  def forall(p: T => Boolean): Events[Boolean] = count(p).map(_ == size)

  /** Stream with boolean values indicating if some element satisfied a predicate.
   */
  def exists(p: T => Boolean): Events[Boolean] = count(p).map(_ > 0)

  /** Stream with the sizes of this container.
   */
  def sizes(implicit s: Spec[T]): Events[Int] = new RContainer.Sizes[T](this)

  // def map[@spec(Int, Long, Double) S](f: T => S): RContainer[S]

  // def filter(p: T => Boolean): RContainer[T]

  // def collect[S <: AnyRef](pf: PartialFunction[T, S])(implicit e: T <:< AnyRef):
  //   RContainer[S]

  // def union(that: RContainer[T])(
  //   implicit count: RContainer.Union.Count[T], a: Arrayable[T]
  // ): RContainer[T]

  // def to[That <: RContainer[T]](implicit factory: RBuilder.Factory[T, That]): That

  // def toAbelian(z: T)(op: (T, T) => T)(inv: (T, T) => T): Signal[T]

  // def toAggregate(z: T)(op: (T, T) => T): Signal[T]

  // def toCommutative(z: T)(op: (T, T) => T): Signal[T]

}


object RContainer {

  @implicitNotFound(
    msg = "Cannot construct a container of type ${That} with elements of type ${S}.")
  trait Factory[@spec(Int, Long, Double) S, That <: RContainer[S]] {
    def apply(inserts: Events[S], removes: Events[S]): That
  }

  /* operations */

  class Count[@spec(Int, Long, Double) T](
    val self: RContainer[T],
    val pred: T => Boolean
  ) extends Events[Int] {
    def newCountInsertObserver(obs: Observer[Int], initial: Int) =
      new CountInsertObserver(obs, initial, pred)
    def newCountRemoveObserver(obs: Observer[Int], insertObs: CountInsertObserver[T]) =
      new CountRemoveObserver(obs, insertObs, pred)
    def initialCount(s: RContainer[T]) = {
      var cnt = 0
      self.foreach(x => if (pred(x)) cnt += 1)
      cnt
    }
    def onReaction(obs: Observer[Int]): Subscription = {
      val initial = initialCount(self)
      val insertObs = newCountInsertObserver(obs, initial)
      val removeObs = newCountRemoveObserver(obs, insertObs)
      new Subscription.Composite(
        self.inserts.onReaction(insertObs),
        self.inserts.onReaction(removeObs)
      )
    }
  }

  class CountInsertObserver[@spec(Int, Long, Double) T](
    val target: Observer[Int],
    var count: Int,
    val pred: T => Boolean
  ) extends Observer[T] {
    var done = false
    def init(self: Observer[T]) {
      target.react(count)
    }
    init(this)
    def react(x: T) = if (!done) {
      if (pred(x)) {
        count += 1
        target.react(count)
      }
    }
    def except(t: Throwable) = if (!done) {
      target.except(t)
    }
    def unreact() = if (!done) {
      done = true
      target.unreact()
    }
  }

  class CountRemoveObserver[@spec(Int, Long, Double) T](
    val target: Observer[Int],
    val insertObs: CountInsertObserver[T],
    val pred: T => Boolean
  ) extends Observer[T] {
    def react(x: T) = if (!insertObs.done) {
      if (pred(x)) {
        insertObs.count -= 1
        target.react(insertObs.count)
      }
    }
    def except(t: Throwable) = insertObs.except(t)
    def unreact() = insertObs.unreact()
  }

  class Sizes[@spec(Int, Long, Double) T](
    val self: RContainer[T]
  ) extends Events[Int] {
    def newSizesInsertObserver(obs: Observer[Int], self: RContainer[T]) =
      new SizesInsertObserver[T](obs, self.size)
    def newSizesRemoveObserver(obs: Observer[Int], insertObs: SizesInsertObserver[T]) =
      new SizesRemoveObserver[T](obs, insertObs)
    def onReaction(obs: Observer[Int]): Subscription = {
      val insertObs = newSizesInsertObserver(obs, self)
      val removeObs = newSizesRemoveObserver(obs, insertObs)
      new Subscription.Composite(
        self.inserts.onReaction(insertObs),
        self.inserts.onReaction(removeObs)
      )
    }
  }

  class SizesInsertObserver[@spec(Int, Long, Double) T](
    val target: Observer[Int],
    var count: Int
  ) extends Observer[T] {
    var done = false
    def init(self: Observer[T]) {
      target.react(count)
    }
    init(this)
    def react(x: T) = if (!done) {
      count += 1
      target.react(count)
    }
    def except(t: Throwable) = if (!done) {
      target.except(t)
    }
    def unreact() = if (!done) {
      done = true
      target.unreact()
    }
  }

  class SizesRemoveObserver[@spec(Int, Long, Double) T](
    val target: Observer[Int],
    val insertObs: SizesInsertObserver[T]
  ) extends Observer[T] {
    def react(x: T) = if (!insertObs.done) {
      insertObs.count -= 1
      target.react(insertObs.count)
    }
    def except(t: Throwable) = insertObs.except(t)
    def unreact() = insertObs.unreact()
  }

}
