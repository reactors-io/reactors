package scala.reactive



import scala.collection._
import scala.reactive.util.Monitor



trait EventQ[@spec(Int, Long, Double) T] {

  def enqueue(x: T): Int

  def dequeue(): T

  def size: Int

}


object EventQ {

  abstract class Factory extends Serializable {
    def newInstance[@spec(Int, Long, Double) T: Arrayable]: EventQ[T]
  }

  class UnrolledRing[@spec(Int, Long, Double) T: Arrayable]
  extends EventQ[T] {
    private val monitor = new Monitor
    private[reactive] val ring = new scala.reactive.core.UnrolledRing[T]

    def enqueue(x: T): Int = monitor.synchronized {
      ring.enqueue(x)
      ring.size
    }

    def dequeue(): T = monitor.synchronized {
      ring.dequeue()
    }

    def size: Int = monitor.synchronized {
      ring.size
    }
  }

  object UnrolledRing {

    object Factory extends EventQ.Factory {
      def newInstance[@spec(Int, Long, Double) T: Arrayable]: EventQ[T] = {
        new UnrolledRing[T]
      }
    }

  }

}
