package scala.reactive



import scala.collection._
import scala.reactive.util.Monitor



trait EventQ[@spec(Int, Long, Double) T] {

  def enqueue(x: T): Int

  def dequeue(emitter: Events.Emitter[T]): Int

  def size: Int

}


object EventQ {

  abstract class Factory extends Serializable {
    def newInstance[@spec(Int, Long, Double) T: Arrayable]: EventQ[T]
  }

  class Zero[@spec(Int, Long, Double) T: Arrayable]
  extends EventQ[T] {
    def enqueue(x: T) = 0
    def dequeue(emitter: Events.Emitter[T]) = 0
    def size = 0
  }

  def isZero(q: EventQ[_]): Boolean = q.isInstanceOf[Zero[_]]

  class UnrolledRing[@spec(Int, Long, Double) T: Arrayable]
  extends EventQ[T] {
    private val monitor = new Monitor
    private[reactive] val ring = new scala.reactive.core.UnrolledRing[T]

    def enqueue(x: T): Int = monitor.synchronized {
      ring.enqueue(x)
      ring.size
    }

    def dequeue(emitter: Events.Emitter[T]): Int = {
      var remaining = -1
      val x = monitor.synchronized {
        remaining = ring.size - 1
        ring.dequeue()
      }
      emitter react x
      remaining
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
