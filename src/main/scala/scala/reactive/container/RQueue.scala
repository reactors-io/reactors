package scala.reactive
package container



import scala.collection._
import scala.annotation.implicitNotFound



trait RQueue[@spec(Int, Long, Double) T] extends RContainer[T] {

  def enqueue(elem: T): Unit

  def dequeue(): T

  def head: T

}


object RQueue {

  def apply[@spec(Int, Long, Double) T: Arrayable]() = new RUnrolledQueue[T]

  trait Lifted[@spec(Int, Long, Double) T] extends RContainer.Lifted[T] {
    val container: RQueue[T]
    def head: Reactive[T]
  }

}