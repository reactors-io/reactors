package io.reactors
package container



import scala.annotation.implicitNotFound



/** Base class for reactive maps.
 *
 *  Reactive map is a collection of pairs of elements that exposes event streams of map
 *  keys with hints that are values mapped to those keys.
 *
 *  @tparam K       type of the keys in the map
 *  @tparam V       type of the values in the map
 */
trait RMap[@spec(Int, Long, Double) K, V]
extends RContainer[K] {
  /** Returns the value associated with the specified key.
   */
  def apply(k: K): V

  /** Filters and maps the values for which the partial function is defined.
   */
  def collectValue[W](pf: PartialFunction[V, W]): RMap[K, W] =
    new RMap.CollectValue(this, pf)

  /** Converts this map into a container of key-value pairs.
   */
  def pairs: RContainer[(K, V)] = new RMap.Pairs(this)

  /** Converts this reactive map into another reactive map.
   */
  def toMap[That](implicit factory: RMap.Factory[K, V, That]): That = {
    val elements = new Events.Emitter[K]
    val container = factory(inserts union elements, removes)
    for (k <- this) elements.react(k, apply(k).asInstanceOf[AnyRef])
    elements.unreact()
    container
  }
}


object RMap {

  def apply[That](f: That => RMap.On[_, _])(
    implicit rf: RMap.Factory[_, _, That]
  ): That = {
    rf(m => f(m).subscription)
  }

  /** Reacts to insert and remove events on the reactive map.
   */
  abstract class On[@spec(Int, Long, Double) K, V](self: RMap[K, V]) {
    private[reactors] var subscription = Subscription.empty

    private[reactors] def init(b: On[K, V]) {
      subscription = new Subscription.Composite(
        self.inserts.onReaction(insertObserver),
        self.removes.onReaction(removeObserver)
      )
    }
    init(this)

    def insert(k: K, v: V): Unit
    def remove(k: K, v: V): Unit
    def except(t: Throwable) = throw t
    def unreact() = {}
    private[reactors] def insertObserver: Observer[K] =
      new InsertObserver(this)
    private[reactors] def removeObserver: Observer[K] =
      new RemoveObserver(this)
  }

  private[reactors] class InsertObserver[@spec(Int, Long, Double) K, V](
    val self: On[K, V]
  ) extends Observer[K] {
    def react(k: K, v: Any) = self.insert(k, v.asInstanceOf[V])
    def except(t: Throwable) = self.except(t)
    def unreact() = self.unreact()
  }

  private[reactors] class RemoveObserver[@spec(Int, Long, Double) K, V](
    val self: On[K, V]
  ) extends Observer[K] {
    def react(k: K, v: Any) = self.remove(k, v.asInstanceOf[V])
    def except(t: Throwable) = self.except(t)
    def unreact() = self.unreact()
  }

  /** Used to create reactive map objects.
   */
  @implicitNotFound(
    msg = "Cannot create a map of type ${That} with elements of type ${K} and ${V}.")
  trait Factory[@spec(Int, Long, Double) K, V, That] {
    def apply(inserts: Events[K], removes: Events[K]): That
    def apply(f: That => Subscription): That
  }

  private[reactors] class Pairs[@spec(Int, Long, Double) K, @spec(Int, Long, Double) V](
    val self: RMap[K, V]
  ) extends RContainer[(K, V)] {
    def inserts: Events[(K, V)] = self.inserts.zipHint((k, h) => (k, h.asInstanceOf[V]))
    def removes: Events[(K, V)] = self.removes.zipHint((k, h) => (k, h.asInstanceOf[V]))
    def foreach(f: ((K, V)) => Unit) = for (k <- self) f((k, self(k)))
    def size = self.size
    def unsubscribe() = {}
  }

  private[reactors] class CollectValue[@spec(Int, Long, Double) K, V, W](
    val self: RMap[K, V],
    val typedPf: PartialFunction[V, W]
  ) extends RMap[K, W] {
    val pf = typedPf.asInstanceOf[PartialFunction[Any, W]]
    def apply(k: K) = pf(self.apply(k))
    def inserts: Events[K] = self.inserts.collectHint(pf)
    def removes: Events[K] = self.removes.collectHint(pf)
    def foreach(f: K => Unit) =
      for (k <- self) if (pf.isDefinedAt(self(k))) f(k)
    def size = self.count(k => pf.isDefinedAt(self(k))).get
    def unsubscribe() = {}
  }
}
