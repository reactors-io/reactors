package scala.reactive
package container



import scala.collection._
import scala.annotation.implicitNotFound



trait RPriorityQueue[@spec(Int, Long, Double) T] extends RQueue[T] {

  def order: Order[T]

  def enqueue(elem: T): Unit

  def dequeue(): T

  def head: T

}


object RPriorityQueue {

  def apply[@spec(Int, Long, Double) T: Arrayable: Order]() = new RBinaryHeap[T]

  trait Lifted[@spec(Int, Long, Double) T] extends RQueue.Lifted[T] {
    val container: RPriorityQueue[T]
  }

}