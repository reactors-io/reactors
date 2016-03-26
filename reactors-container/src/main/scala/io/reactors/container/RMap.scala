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
trait RMap[@spec(Int, Long, Double) K, V] extends RContainer[K] {
  /** Returns the value stored under the specified key.
   */
  def apply(k: K): V

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
  trait Factory[@spec(Int, Long, Double) K, V, That] {
    def apply(inserts: Events[K], removes: Events[K]): That
  }
}
