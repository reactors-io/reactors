package scala.reactive
package container



import scala.collection._
import scala.annotation.implicitNotFound



trait RMap[@spec(Int, Long, Double) K, V <: AnyRef] extends RContainer[(K, V)] {

  def apply(k: K): V

  def entries: PairContainer[K, V]

  def keys: RContainer[K]

  def values: RContainer[V]

  def react: RMap.Lifted[K, V]

}


object RMap {

  def apply[@spec(Int, Long, Double) K, V >: Null <: AnyRef](implicit can: RHashMap.Can[K, V]) = new RHashMap[K, V]

  implicit def factory[@spec(Int, Long, Double) K, V >: Null <: AnyRef] = new RBuilder.Factory[(K, V), RMap[K, V]] {
    def apply() = RHashMap[K, V]
  }

  implicit def pairFactory[@spec(Int, Long, Double) K, V >: Null <: AnyRef] = new PairBuilder.Factory[K, V, RMap[K, V]] {
    def apply() = RHashMap[K, V]
  }

  trait Lifted[@spec(Int, Long, Double) K, V <: AnyRef] extends RContainer.Lifted[(K, V)] {
    val container: RMap[K, V]
    def apply(key: K): Reactive[V]
  }

}