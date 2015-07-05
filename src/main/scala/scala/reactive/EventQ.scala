package scala.reactive



import scala.collection._
import scala.reactive.util.Monitor



trait EventQ[@spec(Int, Long, Double) T] {

  def enqueue(x: T): Unit

  def dequeue(): T

}


object EventQ {

  abstract class Factory extends Serializable {
    def newInstance[@spec(Int, Long, Double) T: Arrayable]: EventQ[T]
  }

  class UnrolledRing[@spec(Int, Long, Double) T: Arrayable]
  extends EventQ[T] {
    private val monitor = new Monitor
    private[reactive] val ring = new scala.reactive.core.UnrolledRing[T]

    def enqueue(x: T): Unit = monitor.synchronized {
      ring.enqueue(x)
    }

    def dequeue(): T = monitor.synchronized {
      ring.dequeue()
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
