package io.reactors
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

  /** Stream with the reduction of the current set of elements in this container.
   *
   *  Parameters `op`, `inv` and `z` must for an Abelian group, that is, `z` is the
   *  neutral element, and `inv` is the inverse operation of `op`, in the sense that
   *  `inv(op(s, t), t) == op(inv(s, t), t) == s` is always true.
   */
  def reduce[
    @spec(Int, Long, Double) S
  ](z: S)(op: (S, T) => S)(inv: (S, T) => S): Events[S] =
    new RContainer.Reduce[S, T](self, z, op, inv)

  /** Incrementally filters elements from the current container.
   */
  def filter(p: T => Boolean): RContainer[T] = new RContainer.Filter[T](this, p)

  /** Incrementally maps elements from the current container.
   *
   *  Function `f` for the map must not be an injection, that is, for any two elements
   *  `x` and `y` that are not equal (`x != y`), `f(x)` **cannot be equal to** `f(y)`.
   */
  def map[@spec(Int, Long, Double) S](f: T => S): RContainer[S] =
    new RContainer.Map[T, S](this, f)

  /** Incrementally copies this container to another container type.
   *
   *  Materializes another container, such that all the elements from this container are
   *  visible in the target container.
   *
   *  Users may call `unsubscribe` on the resulting container to stop incremental
   *  updates. Losing the container and failing to call `unsubscribe` may result in a
   *  time leak.
   */
  def to[That <: RContainer[T]](implicit factory: RContainer.Factory[T, That]): That = {
    val elements = new Events.Emitter[T]
    val container = factory(inserts union elements, removes)
    for (x <- this) elements.react(x)
    elements.unreact()
    container
  }

  /** Incrementally produces a union of the elements in the two containers.
   */
  def union(that: RContainer[T])(
    implicit a: Arrayable[T], h: Hash[T]
  ): RContainer[T] =
    new RContainer.Union(this, that)

  // def groupBy

  // def toAggregate(z: T)(op: (T, T) => T): Signal[T]

  // def toCommutativeAggregate(z: T)(op: (T, T) => T): Signal[T]

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
        self.removes.onReaction(removeObs)
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
        self.removes.onReaction(removeObs)
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

  class Reduce[@spec(Int, Long, Double) S, @spec(Int, Long, Double) T](
    val self: RContainer[T],
    val z: S,
    val op: (S, T) => S,
    val inv: (S, T) => S
  ) extends Events[S] {
    def newReduceInsertObserver(obs: Observer[S], initial: S) =
      new ReduceInsertObserver(obs, initial, op, inv)
    def newReduceRemoveObserver(obs: Observer[S], insobs: ReduceInsertObserver[S, T]) =
      new ReduceRemoveObserver(obs, insobs, op, inv)
    def initialReduce(s: RContainer[T]) = {
      var s = z
      self.foreach(x => s = op(s, x))
      s
    }
    def onReaction(obs: Observer[S]): Subscription = {
      val initial = initialReduce(self)
      val insertObs = newReduceInsertObserver(obs, initial)
      val removeObs = newReduceRemoveObserver(obs, insertObs)
      new Subscription.Composite(
        self.inserts.onReaction(insertObs),
        self.removes.onReaction(removeObs)
      )
    }
  }

  class ReduceInsertObserver[@spec(Int, Long, Double) S, @spec(Int, Long, Double) T](
    val target: Observer[S],
    var current: S,
    val op: (S, T) => S,
    val inv: (S, T) => S
  ) extends Observer[T] {
    var done = false
    def init(target: Observer[S]) {
      target.react(current)
    }
    init(target)
    def react(x: T): Unit = if (!done) {
      current = try {
        op(current, x)
      } catch {
        case NonLethal(t) =>
          except(t)
          return
      }
      target.react(current)
    }
    def except(t: Throwable) = if (!done) {
      target.except(t)
    }
    def unreact() = if (!done) {
      done = true
      target.unreact()
    }
  }

  class ReduceRemoveObserver[@spec(Int, Long, Double) S, @spec(Int, Long, Double) T](
    val target: Observer[S],
    val insertObs: ReduceInsertObserver[S, T],
    val op: (S, T) => S,
    val inv: (S, T) => S
  ) extends Observer[T] {
    def react(x: T): Unit = if (!insertObs.done) {
      insertObs.current = try {
        inv(insertObs.current, x)
      } catch {
        case NonLethal(t) =>
          except(t)
          return
      }
      target.react(insertObs.current)
    }
    def except(t: Throwable) = insertObs.except(t)
    def unreact() = insertObs.unreact()
  }

  class Filter[@spec(Int, Long, Double) T](
    val self: RContainer[T],
    val pred: T => Boolean
  ) extends RContainer[T] {
    def inserts: Events[T] = self.inserts.filter(pred)
    def removes: Events[T] = self.removes.filter(pred)
    def unsubscribe() {}
    def size: Int = self.count(pred).get
    def foreach(f: T => Unit): Unit = self.foreach(x => if (pred(x)) f(x))
  }

  class Map[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](
    val self: RContainer[T],
    val f: T => S
  ) extends RContainer[S] {
    def inserts: Events[S] = self.inserts.map(f)
    def removes: Events[S] = self.removes.map(f)
    def unsubscribe() {}
    def size: Int = self.size
    def foreach(g: S => Unit): Unit = self.foreach(x => g(f(x)))
  }

  class Union[@spec(Int, Long, Double) T](
    val self: RContainer[T],
    val that: RContainer[T]
  )(
    implicit val arrayable: Arrayable[T],
    val hash: Hash[T]
  ) extends RContainer[T] {
    private[reactors] var countMap: RHashMap[T, Union.Num] = _
    private[reactors] var insertsEmitter: Events.Emitter[T] = _
    private[reactors] var removesEmitter: Events.Emitter[T] = _
    private[reactors] var subscription: Subscription = _
    def init(u: Union[T]) {
      countMap = new RHashMap[T, Union.Num]
      insertsEmitter = new Events.Emitter[T]
      removesEmitter = new Events.Emitter[T]
      subscription = new Subscription.Composite(
        (self.inserts union that.inserts).onEvent(x => insertUpdate(x)),
        (self.removes union that.removes).onEvent(x => removeUpdate(x))
      )
    }
    init(this)
    def insertUpdate(x: T) {
      val n = countMap.applyOrNil(x)
      if (n == countMap.nil) {
        countMap(x) = Union.one
        insertsEmitter.react(x)
      } else if (n == Union.one) {
        countMap(x) = Union.two
      } // else ignore
    }
    def removeUpdate(x: T) {
      val n = countMap.applyOrNil(x)
      if (n == countMap.nil) {
        // ignore
      } else if (n == Union.one) {
        countMap.remove(x)
        removesEmitter.react(x)
      } else if (n == Union.two) {
        countMap(x) = Union.one
      }
    }
    def inserts: Events[T] = insertsEmitter
    def removes: Events[T] = removesEmitter
    def unsubscribe() = subscription.unsubscribe()
    def size = countMap.size
    def foreach(f: T => Unit) = countMap.foreachKey(f)
  }

  object Union {
    abstract class Num {
      def apply(): Int
      def inc: Num
      def dec: Num
    }

    val one: Num = new Num {
      def apply() = 1
      def inc = two
      def dec = sys.error("one")
    }

    val two: Num = new Num {
      def apply() = 2
      def inc = sys.error("two")
      def dec = one
    }
  }

}
