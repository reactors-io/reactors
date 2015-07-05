package scala.reactive



import scala.collection._
import scala.reactive.isolate.Frame



trait Chan[@spec(Int, Long, Double) T] extends Identifiable {

  def !(x: T): Unit

  def isSealed: Boolean
}


object Chan {

  class Local[@spec(Int, Long, Double) T](
    val uid: Long,
    val queue: EventQ[T],
    val frame: Frame
  ) extends Chan[T] {

    def !(x: T): Unit = frame.enqueueEvent(uid, queue, x)

    def isSealed: Boolean = frame.isConnectorSealed(uid)

  }

}
