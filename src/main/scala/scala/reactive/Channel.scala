package scala.reactive



import scala.collection._
import scala.reactive.isolate.Frame



trait Channel[@spec(Int, Long, Double) T] extends Identifiable {

  def !(x: T): Unit

  def isSealed: Boolean
}


object Channel {

  class Local[@spec(Int, Long, Double) T](
    val uid: Long,
    val queue: EventQueue[T],
    val frame: Frame
  ) extends Channel[T] {
    private[reactive] var isOpen = true

    def !(x: T): Unit = if (isOpen) frame.enqueueEvent(uid, queue, x)

    def isSealed: Boolean = !isOpen

  }

}
