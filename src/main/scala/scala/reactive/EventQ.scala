package scala.reactive



import scala.collection._



trait EventQ[@spec(Int, Long, Double) T] {

  def enqueue(x: T): Unit

}
