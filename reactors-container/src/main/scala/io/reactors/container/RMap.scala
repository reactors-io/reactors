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

  // TODO: Implement `mapValue`, `collectValue`, `swap` and `onChange`.

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
  /** Used to create reactive map objects.
   */
  trait Factory[@spec(Int, Long, Double) K, V, That] {
    def apply(inserts: Events[K], removes: Events[K]): That
  }

  /** A key-value pair view of a reactive map.
   */
  class Pairs[@spec(Int, Long, Double) K, @spec(Int, Long, Double) V](
    val self: RMap[K, V]
  ) extends RContainer[(K, V)] {
    def inserts: Events[(K, V)] = self.inserts.zipHint((k, h) => (k, h.asInstanceOf[V]))
    def removes: Events[(K, V)] = self.removes.zipHint((k, h) => (k, h.asInstanceOf[V]))
    def foreach(f: ((K, V)) => Unit) = for (k <- self) f((k, self(k)))
    def size = self.size
    def unsubscribe() = {}
  }
}
