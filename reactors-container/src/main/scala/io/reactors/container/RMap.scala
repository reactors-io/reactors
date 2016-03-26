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
}


object RMap {
  trait Factory[@spec(Int, Long, Double) K, V, That] {
    def apply(inserts: Events[K], removes: Events[K]): That
  }
}
