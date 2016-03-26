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
) extends RContainer[XY] {
  private[reactors] var rawSize = 0
  private[reactors] var matrix: HashMatrix[T] = null
  private[reactors] var insertsEmitter: Events.Emitter[XY] = null
  private[reactors] var removesEmitter: Events.Emitter[XY] = null
  private[reactors] var pairInsertsEmitter: Events.Emitter[XY] = null
  private[reactors] var pairRemovesEmitter: Events.Emitter[XY] = null
  private[reactors] var rawMap: RHashMatrix.AsMap[T] = null
  private[reactors] var subscription: Subscription = null

  protected def init(self: RHashMatrix[T]) {
    matrix = new HashMatrix[T]
    insertsEmitter = new Events.Emitter[XY]
    removesEmitter = new Events.Emitter[XY]
    pairInsertsEmitter = new Events.Emitter[XY]
    pairRemovesEmitter = new Events.Emitter[XY]
    subscription = Subscription.empty
    rawMap = new RHashMatrix.AsMap(this)
  }

  init(this)

  /** Returns an `RMap` view of this matrix. May result in boxing if the matrix contains
   *  primitive values.
   */
  def asMap: RMap[XY, T] = rawMap

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
      notifyRemove(x, y, v)
      rawSize -= 1
    }
    if (v != nil) {
      notifyInsert(x, y, v)
      rawSize += 1
    }
    prev
  }

  private[reactors] def notifyInsert(x: Int, y: Int, v: T) {
    if (insertsEmitter.hasSubscriptions)
      insertsEmitter.react(XY(x, y), null)
    if (pairInsertsEmitter.hasSubscriptions)
      pairInsertsEmitter.react(XY(x, y), v.asInstanceOf[AnyRef])
  }

  private[reactors] def notifyRemove(x: Int, y: Int, v: T) {
    if (removesEmitter.hasSubscriptions)
      removesEmitter.react(XY(x, y), null)
    if (pairRemovesEmitter.hasSubscriptions)
      pairInsertsEmitter.react(XY(x, y), v.asInstanceOf[AnyRef])
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

  class AsMap[T](
    val self: RHashMatrix[T]
  ) extends RMap[XY, T] {
    def apply(xy: XY): T = self.apply(XY.xOf(xy), XY.yOf(xy))
    def inserts = self.pairInsertsEmitter
    def removes = self.pairRemovesEmitter
    def foreach(f: XY => Unit) = self.foreach(f)
    def size = self.size
    def unsubscribe() = {}
  }
}
