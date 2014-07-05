package scala.reactive
package calc






/** A function from an object `T` to a value `S`.
 *
 *  '''Note:''' A Scala standard library function that maps from primitive types
 *  to value types will undergo boxing.
 *  This trait exists to avoid this.
 *
 *  @param T      the source primitive type `T`
 *  @param S      the target value type `S`
 */
trait RefValFun[-T, @specialized(Int, Long, Double) +S] {
  def apply(x: T): S
}
