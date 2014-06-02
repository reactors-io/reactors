package scala.reactive
package container



import scala.collection._
import scala.annotation.implicitNotFound



trait ReactQueue[@spec(Int, Long, Double) T] extends ReactContainer[T] {

  def enqueue(elem: T): Unit

  def dequeue(): T

  def head: T

}


object ReactQueue {

  def apply[@spec(Int, Long, Double) T: Arrayable]() = new ReactUnrolledQueue[T]

  trait Lifted[@spec(Int, Long, Double) T] extends ReactContainer.Lifted[T] {
    val container: ReactQueue[T]
    def head: Reactive[T]
  }

  implicit def factory[@spec(Int, Long, Double) T: Arrayable] = new ReactBuilder.Factory[T, ReactQueue[T]] {
    def apply() = ReactUnrolledQueue[T]
  }

}