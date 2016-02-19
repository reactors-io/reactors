package scala.reactive
package container



import scala.collection._
import scala.annotation.implicitNotFound



/** A reactive map is a reactive counterpart of a mutable map.
 *
 *  Calling the `react` method of the `RMap` type returns.
 *
 *  @tparam K         the type of the keys in the reactive map
 *  @tparam V         the type of the values in the reactive map
 */
trait RMap[@spec(Int, Long, Double) K, V <: AnyRef] extends RContainer[(K, V)] {

  /** The special value that represents an absence of the value
   *  under the specified key.
   *
   *  @return         the nil value
   */
  def nil: V

  /** Returns the value associated with the specified key or throws an exception if the key is not present.
   *
   *  @param k        the key
   *  @return         the value associated with the key
   */
  def apply(k: K): V

  /** Returns the value associated with the specified key or a nil value if the key is not present.
   *
   *  @param k        the key
   *  @return         the value associated with the key, or a nil value
   */
  def applyOrNil(k: K): V
  
  /** Optionally returns the value associated with the key, if it is present.
   *
   *  @param k        the key
   *  @return         the optional value associated with the key
   */
  def get(k: K): Option[V]

  /** Returns `true` iff the map contains the specified key.
   */
  def contains(k: K): Boolean

  /** Updates the key in the map with a new value.
   *
   *  If the key already existed in the map, the existing value is replaced with the new value.
   *
   *  @param key      the key
   *  @param value    the new value to associate with the key
   */
  def update(key: K, value: V): Unit

  /** Removes the key from the map.
   *
   *  @param key      the key to remove
   *  @return         `true` if the key was previously in the map, `false` otherwise
   */
  def remove(key: K): Boolean

  /** Returns the view over pairs in this map.
   */
  def entries: PairContainer[K, V]

  /** Returns the view over keys in this map.
   */
  def keys: RContainer[K]

  /** Returns the view over values in this map.
   *
   *  This method may only be used if the reactive map is injective,
   *  that is, no two keys are mapped into the same value.
   */
  def values: RContainer[V]

  /** Returns the lifted view of the reactive map, used to obtain reactive values in this map.
   */
  def react: RMap.Lifted[K, V]

}


object RMap {

  /** Factory method for default reactive map creation.
   */
  def apply[@spec(Int, Long, Double) K, V >: Null <: AnyRef](
    implicit can: RHashMap.Can[K, V], hash: Hash[K]
  ) = {
    new RHashMap[K, V]
  }

  /** Reactive builder factory, automatically used for transforming containers into
   *  reactive maps.
   */
  implicit def factory[@spec(Int, Long, Double) K, V >: Null <: AnyRef](
    implicit can: RHashMap.Can[K, V], hash: Hash[K]
  ) = new RBuilder.Factory[(K, V), RMap[K, V]] {
    def apply() = RHashMap[K, V]
  }

  /** Reactive pair builder factory, automatically used for transforming pair containers
   *  into reactive maps.
   */
  implicit def pairFactory[@spec(Int, Long, Double) K, V >: Null <: AnyRef](
    implicit can: RHashMap.Can[K, V], hash: Hash[K]
  ) = new PairBuilder.Factory[K, V, RMap[K, V]] {
    def apply() = RHashMap[K, V]
  }

  /** Lifted view of the reactive map.
   */
  trait Lifted[@spec(Int, Long, Double) K, V <: AnyRef] extends RContainer.Lifted[(K, V)] {
    /** The original reactive map container.
     */
    val container: RMap[K, V]

    /** Returns the event stream associated with the key.
     *
     *  The event stream emits an event whenever a new value
     *  is subsequently assigned to the specified key.
     *
     *  @param key         the key
     *  @return            the reactive containing all the values assigned to the key
     */
    def apply(key: K): Events[V]

    /** Returns the optional event stream associated with the key.
     *
     *  The event stream emits a `Some` event whenever a new value is assigned,
     *  or `None` if no value is assigned.
     *
     *  @param key         the key
     *  @return            the reactive containing all the optional value at this key
     */
    def get(key: K): Events[Option[V]]
  }

}
