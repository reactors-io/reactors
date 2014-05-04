package org.reactress
package algebra






/** A bijective function between values of two types `T` and `S`.
 *
 *  A bijection maps every element of type `T` into exactly one
 *  element of type `S`, and every element of type `S` into exactly
 *  one element of type `T`.
 *  It is a function and its inverse bundled together.
 *  Additionally, it has the property that `b.inv(b.apply(x)) == x`
 *  and `b.apply(b.inv(x)) == x`.
 *
 *  @param T      the source type `T`
 *  @param S      the target type `S`
 */
trait Bijection[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
extends (T => S) {
  /** Maps an element of type `T` to `S`.
   */
  def apply(t: T): S

  /** Inverse mapping from `S` to `T`.
   */
  def inv(s: S): T

  /** Produces a new bijection that is the inverse of this bijection --
   *  i.e. it swaps the arguments around.
   *  
   *  This is unlike `inv` which just inverse-maps an element.
   *  To just map in the inverse direction, use `inv`.
   */
  def inverse: Bijection[S, T] = this match {
    case Bijection.Inverted(b) => b
    case b => Bijection.Inverted(b)
  }
}


object Bijection {

  def apply[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](f: T => S, i: S => T) = new Bijection[T, S] {
    def apply(t: T): S = f(t)
    def inv(s: S): T = i(s)
  }

  case class Inverted[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](b: Bijection[T, S])
  extends Bijection[S, T] {
    def apply(s: S) = b.inv(s)
    def inv(t: T) = b.apply(t)
  }

}
