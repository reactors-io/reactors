package org.reactress
package isolate



import scala.annotation._
import scala.collection._



trait EventQueue[@spec(Int, Long, Double) Q]
extends Enqueuer[Q] {
  def foreach(f: IsolateFrame[_, Q])(implicit scheduler: Scheduler): Unit
  def dequeue(): Q
  def isEmpty: Boolean
  def nonEmpty = !isEmpty
}


object EventQueue {

  class SyncedUnrolledRing[@spec(Int, Long, Double) Q: Arrayable](val monitor: AnyRef)
  extends EventQueue[Q] {
    private[reactress] val ring = new util.UnrolledRing[Q]

    private[reactress] var frames = List[IsolateFrame[_, Q]]()

    def +=(elem: Q) = {
      val fs = monitor.synchronized {
        ring.enqueue(elem)
        frames
      }
      wakeAll(fs)
    }

    @tailrec private def wakeAll(frames: List[IsolateFrame[_, Q]]): Unit = frames match {
      case frame :: more =>
        @tailrec def wake(frame: IsolateFrame[_, Q]): Unit = if (!frame.isOwned) {
          if (frame.tryOwn()) frame.scheduler.schedule(frame)
          else wake(frame)
        }
        wake(frame)
        wakeAll(more)
      case Nil =>
        // done
    }

    def foreach(f: IsolateFrame[_, Q])(implicit scheduler: Scheduler) = monitor.synchronized {
      frames ::= f
    }

    def isEmpty = monitor.synchronized { ring.isEmpty }

    def dequeue(): Q = monitor.synchronized {
      ring.dequeue()
    }
  }

}
