package io.reactors
package common



import scala.collection._
import scala.reflect.ClassTag



/** A collection that is a set and a sequence, simultaneously.
 */
class IndexedSet[T >: Null <: AnyRef] {
  private val buffer = mutable.ArrayBuffer[T]()
  private val index = mutable.Map[T, Int]()

  def size = buffer.size

  def length = size

  def apply(i: Int) = buffer(i)

  def +=(x: T) = if (!index.contains(x)) {
    buffer += x
    index(x) = buffer.length - 1
  }

  def -=(x: T) = if (index.contains(x)) {
    val idx = index(x)
    val last = buffer.last
    buffer(idx) = last
    buffer.remove(buffer.length - 1)
    index(last) = idx
  }
}
