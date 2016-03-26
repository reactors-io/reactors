package io.reactors
package container



import io.reactors.algebra.XY
import io.reactors.common.HashMatrix
import scala.collection._
import scala.reflect.ClassTag



/** A reactive hash matrix.
 *
 *  @tparam T       type of the keys in the map, specialized
 */
class RHashMatrix[@spec(Int, Long, Double) T](
  implicit val arrayable: Arrayable[T]
) extends RMap[XY, T] {
  private[reactors] var rawSize = 0
  private[reactors] var matrix: HashMatrix[T] = null
  private[reactors] var insertsEmitter: Events.Emitter[XY] = null
  private[reactors] var removesEmitter: Events.Emitter[XY] = null
  private[reactors] var subscription: Subscription = null

  protected def init(self: RHashMatrix[T]) {
    matrix = new HashMatrix[T]
    insertsEmitter = new Events.Emitter[XY]
    removesEmitter = new Events.Emitter[XY]
    subscription = Subscription.empty
  }

  init(this)

  /** Returns the value stored at the specified coordinates, or `nil` otherwise.
   */
  def apply(x: Int, y: Int): T = matrix(x, y)

  /** Returns the value stored at the specified coordinates, or `elem` otherwise.
   */
  def orElse(x: Int, y: Int, elem: T) = matrix.orElse(x, y, elem)

  /** Update the value at the specified coordinates.
   */
  def update(x: Int, y: Int, v: T): Unit = set(x, y, v)

  /** Updates the value at the specified coordinates, and returns the previous value.
   */
  def set(x: Int, y: Int, v: T): T = {
    val prev = matrix.applyAndUpdate(x, y, v)

    if (prev != nil) {
      if (removesEmitter.hasSubscriptions)
        removesEmitter.react(XY(x, y), prev.asInstanceOf[AnyRef])
      rawSize -= 1
      if (v != nil) {
        if (insertsEmitter.hasSubscriptions)
          insertsEmitter.react(XY(x, y), v.asInstanceOf[AnyRef])
        rawSize += 1
      }
    } else {
      if (v != nil) rawSize += 1
    }

    prev
  }

  /** Sets the value at the specified coordinates to `nil`.
   */
  def remove(x: Int, y: Int): T = set(x, y, nil)

  /** Clears the entire matrix.
   */
  def clear() = {
    matrix.clear()
    rawSize = 0
  }

  /** Copies the contents of the specified rectangle into an array.
   */
  def copy(array: Array[T], fromx: Int, fromy: Int, untilx: Int, untily: Int): Unit = {
    matrix.copy(array, fromx, fromy, untilx, untily)
  }

  /** Returns a view used to traverse all elements in a rectangle.
   */
  def area(gxf: Int, gyf: Int, gxu: Int, gyu: Int): HashMatrix.Area[T] =
    matrix.area(gxf, gyf, gxu, gyu)

  /** Returns a view used to traverse non-`nil` elements in a rectangle.
   */
  def nonNilArea(gxf: Int, gyf: Int, gxu: Int, gyu: Int): HashMatrix.Area[T] =
    matrix.nonNilArea(gxf, gyf, gxu, gyu)

  /** Returns the default, `nil` value for this matrix.
   */
  def nil: T = matrix.nil

  /** Traverses all the non-`nil` values in this matrix.
   */
  def foreach(f: XY => Unit): Unit = matrix.foreach(f)

  def inserts: Events[XY] = insertsEmitter

  def removes: Events[XY] = removesEmitter

  def size: Int = rawSize

  def unsubscribe() = subscription.unsubscribe()

}


object RHashMatrix {
  implicit def factory[@spec(Int, Long, Double) T](
    implicit a: Arrayable[T]
  ): RContainer.Factory[(Int, Int, T), RHashMatrix[T]] = {
    new RContainer.Factory[(Int, Int, T), RHashMatrix[T]] {
      def apply(inserts: Events[(Int, Int, T)], removes: Events[(Int, Int, T)]):
        RHashMatrix[T] = {
        val hm = new RHashMatrix[T]
        hm.subscription = new Subscription.Composite(
          inserts.onEvent({ case (x, y, v) => hm.update(x, y, v) }),
          removes.onEvent({ case (x, y, v) => hm.remove(x, y) })
        )
        hm
      }
    }
  }
}
