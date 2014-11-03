package scala.reactive
package container



import scala.reflect.ClassTag



/** A reactive set container is a reactive counterpart of a mutable set.
 *
 *  @tparam T           the type of the elements in this set
 */
trait RSet[@spec(Int, Long, Double) T]
extends RContainer[T] {
  self =>

  /** Adds an element to the set.
   */
  def +=(elem: T): Boolean

  /** Removes the equivalent element from the set.
   */
  def -=(elem: T): Boolean

  /** Returns a reactive view of this reactive set.
   */
  def react: RSet.Lifted[T]

  /** Returns true iff the equivalent element is in the set.
   */
  def apply(elem: T): Boolean

  /** Returns true iff the equivalent element is in the set.
   */
  def contains(elem: T): Boolean

  /** Adds an element to the set.
   *
   *  @param elem       the element to add
   *  @returns          `true` if the element was not present, `false` otherwise
   */
  def add(elem: T): Boolean

  /** Removes an element from the set
   *
   *  @param elem       the element to remove
   *  @returns          `true` if the element was remove, `false` otherwise
   */
  def remove(elem: T): Boolean

  /** Removes all the elements from the set.
   */
  def clear(): Unit

}


object RSet {

  def apply[@spec(Int, Long, Double) T: Arrayable]() = new RHashSet[T]

  trait Lifted[@spec(Int, Long, Double) T] extends RContainer.Lifted[T]

  implicit def factory[@spec(Int, Long, Double) T: Arrayable] = new RBuilder.Factory[T, RSet[T]] {
    def apply() = RHashSet[T]
  }

}





