package scala.reactive



import scala.collection._
import scala.reactive.util.Monitor



/** A queue that buffers events that arrive on the corresponding channel.
 */
trait EventQueue[@spec(Int, Long, Double) T] {

  def enqueue(x: T): Int

  def dequeue(emitter: Events.Emitter[T]): Int

  def size: Int

}


object EventQueue {

  abstract class Factory extends Serializable {
    def newInstance[@spec(Int, Long, Double) T: Arrayable]: EventQueue[T]
  }

  class Zero[@spec(Int, Long, Double) T: Arrayable]
  extends EventQueue[T] {
    def enqueue(x: T) = 0
    def dequeue(emitter: Events.Emitter[T]) = 0
    def size = 0
  }

  def isZero(q: EventQueue[_]): Boolean = q.isInstanceOf[Zero[_]]

  class UnrolledRing[@spec(Int, Long, Double) T: Arrayable](
    private[reactive] val monitor: Monitor = new Monitor
  ) extends EventQueue[T] {
    private[reactive] val ring = new scala.reactive.common.UnrolledRing[T]

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

    object Factory extends EventQueue.Factory {
      def newInstance[@spec(Int, Long, Double) T: Arrayable]: EventQueue[T] = {
        new UnrolledRing[T]
      }
    }

  }

}
