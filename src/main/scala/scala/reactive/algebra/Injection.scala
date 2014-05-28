package scala.reactive
package algebra






/** An injective function between values of two types `T` and `S`.
 *
 *  In mathematics, an injection maps elements of type `T` into exactly one
 *  element of type `S`, and can map some elements of type `S` into
 *  one element of type `T`.
 *  An injection is undefined for some values in `S`.
 *  Here, an injection may also be undefined for some elements in `T`.
 *  An injection is a partial function and its partial inverse bundled together.
 *
 *  For example, a mapping from natural numbers to twice their value
 *  forms an injection `1 -> 2`, `2 -> 4`, `3 -> 6` and so on.
 *  The inverse is not defined for every natural number --
 *  `2` is mapped to `1`, but `3` is not mapped to anything.
 *
 *  Additionally, it has the property that `b.inv(b.apply(x)) == x`.
 *  If `b.inv` is defined for `x`, then `b.apply(b.inv(x)) == x`.
 *
 *  '''Note:'''
 *  `Injection` is not specialized, and neither is the standard library
 *  `PartialFunction` class.
 *  
 *  @param T      the source type `T`
 *  @param S      the target type `S`
 */
trait Injection[T, S] {
  self =>

  def isDefinedAt(t: T): Boolean

  /** Maps an element of type `T` to `S`.
   */
  def apply(t: T): S

  /** Checks if value `s` of type `S` maps back to `T`.
   */
  def isDefinedInverse(s: S): Boolean

  /** Inverse mapping from `S` to `T`.
   */
  def invert(s: S): T

  /** Get the partial function version of this injection.
   *
   *  To get the partial function version of the inverse,
   *  call `f.inverse.function`.
   */
  def function: PartialFunction[T, S] = new PartialFunction[T, S] {
    def isDefinedAt(t: T) = self.isDefinedAt(t)
    def apply(t: T) = self.apply(t)
  }

  /** Returns a new injection, which is the inverse of this one.
   *
   *  This is different than calling `invert`,
   *  which just maps a single value.
   */
  def inverse: Injection[S, T] = this match {
    case Injection.Inverted(i) => i
    case i => Injection.Inverted(i)
  }
}


/** Contains factory methods for injection instances.
 */
object Injection {

  /** An inversion of an injection.
   *
   *  @tparam T     the source type `T`
   *  @tparam S     the target type `S`
   *  @param i      the injection being inverted
   */
  case class Inverted[T, S](val i: Injection[S, T]) extends Injection[T, S] {
    def isDefinedAt(t: T) = i.isDefinedInverse(t)
    def apply(t: T): S = i.invert(t)
    def isDefinedInverse(s: S) = i.isDefinedAt(s)
    def invert(s: S): T = i(s)
  }

  /** Creates an injection instance using two partial functions.
   *
   *  @tparam T     the source type
   *  @tparam S     the target type of the injection
   *  @param f      the partial function from `T` to `S`
   *  @param i      the partial function for the inverse
   *  @return       a new injection instance
   */
  def apply[T, S](f: PartialFunction[T, S], i: PartialFunction[S, T]) = new Injection[T, S] {
    def isDefinedAt(t: T) = f.isDefinedAt(t)
    def apply(t: T): S = f(t)
    def isDefinedInverse(s: S) = i.isDefinedAt(s)
    def invert(s: S): T = i(s)
  }

}
