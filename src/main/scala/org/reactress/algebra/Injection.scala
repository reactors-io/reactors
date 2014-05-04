package org.reactress
package algebra






/** An injective function between values of two types `T` and `S`.
 *
 *  An injection maps every element of type `T` into exactly one
 *  element of type `S`, and can map some elements of type `S` into
 *  one element of type `T`.
 *  It is a function and its partial inverse bundled together.
 *
 *  For example, a mapping from natural numbers to twice their value
 *  forms an injection `1 -> 2`, `2 -> 4`, `3 -> 6` and so on.
 *  The inverse is not defined for every natural number --
 *  `2` is mapped to `1`, but `3` is not mapped to anything.
 *
 *  Additionally, it has the property that `b.inv(b.apply(x)) == x`.
 *  If `b.inv` is defined for `x`, then `b.apply(b.inv(x)) == x`.
 *
 *  @param T      the source type `T`
 *  @param S      the target type `S`
 */
trait Injection[T, S] extends (T => S) {
  /** Maps an element of type `T` to `S`.
   */
  def apply(t: T): S

  /** Checks if value `s` of type `S` maps back to `T`.
   */
  def isDefinedAt(s: S): Boolean

  /** Inverse mapping from `S` to `T`.
   */
  def inv(s: S): T

  /** Returns a partial inverse.
   */
  def inverse: PartialFunction[S, T]
}


object Inverse {

  def apply[T, S](f: T => S, i: PartialFunction[S, T]) = new Injection[T, S] {
    def apply(t: T): S = f(t)
    def isDefinedAt(s: S) = i.isDefinedAt(s)
    def inv(s: S): T = i(s)
    def inverse = i
  }

}
