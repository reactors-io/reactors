package scala.reactive
package container



import scala.collection._
import scala.annotation.implicitNotFound



trait ReactPriorityQueue[@spec(Int, Long, Double) T] extends ReactQueue[T] {

  def order: Order[T]

  def enqueue(elem: T): Unit

  def dequeue(): T

  def head: T

}


object ReactPriorityQueue {

  def apply[@spec(Int, Long, Double) T: Arrayable: Order]() = new ReactBinaryHeap[T]

  trait Lifted[@spec(Int, Long, Double) T] extends ReactQueue.Lifted[T] {
    val container: ReactPriorityQueue[T]
  }

}