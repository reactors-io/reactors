package io.reactors
package container



import io.reactors.common.HashMatrix
import scala.collection._
import scala.reflect.ClassTag



/** A reactive hash map.
 *
 *  In addition to standard `inserts` and `removes`, and other container event streams,
 *  reactive hash maps expose event streams with elements at specific keys.
 *
 *  @tparam T       type of the keys in the map, specialized
 */
class RHashMatrix[@spec(Int, Long, Double) T](
  implicit val arrayable: Arrayable[T]
) extends RContainer[T] {
  private[reactors] val matrix = new HashMatrix[T]

  def apply(x: Int, y: Int): T = matrix(x, y)

  def update(x: Int, y: Int, v: T): Unit = matrix(x, y) = v

  def nil: T = matrix.nil

  def foreach(f: T => Unit): Unit = ???

  def inserts: Events[T] = ???

  def removes: Events[T] = ???

  def size: Int = ???

  def unsubscribe() = ???

}


object RHashMatrix {
  implicit def factory[@spec(Int, Long, Double) T](
    implicit a: Arrayable[T]
  ) = {
    ???
  }
}
