package scala.reactive



import scala.collection._



trait EventQ[@spec(Int, Long, Double) T] {

  def enqueue(x: T): Unit

  def dequeue(): T

}


object EventQ {

  abstract class Factory {
    def newInstance[@spec(Int, Long, Double) T](): EventQ[T]
  }

}
