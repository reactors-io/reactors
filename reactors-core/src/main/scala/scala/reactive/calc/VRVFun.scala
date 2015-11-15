package scala.reactive
package calc






/** A function from a value/object `T` to a value `S`.
 *
 *  '''Note:''' A Scala standard library function that maps from primitive types
 *  to value types will undergo boxing.
 *  This trait exists to avoid this.
 *
 *  @param T      the source value type `T`
 *  @param S      the source object type `S`
 */
trait VRVFun[@specialized(Int, Long, Double) -T, -S <: AnyRef, @specialized(Int, Long, Double) R] {
  def apply(x: T, y: S): R
}
