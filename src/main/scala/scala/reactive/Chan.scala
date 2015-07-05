package scala.reactive



import scala.collection._



trait Chan[@spec(Int, Long, Double) T] extends Identifiable {

  def !(x: T): Unit

  def isSealed: Boolean

  def seals: Events[Throwable]

}


object Chan {

  class Local[@spec(Int, Long, Double) T](
    val uid: Long,
    val queue: EventQ[T],
    val frame: Frame,
    val seals: Events.Emitter[Throwable]
  ) extends Chan[T] {

    def !(x: T): Unit = frame.enqueueEvent(queue, x)

    def isSealed: Boolean = frame.isChannelSealed(uid)

  }

}
