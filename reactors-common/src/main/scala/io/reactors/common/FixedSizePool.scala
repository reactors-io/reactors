package io.reactors



import scala.reflect.ClassTag



class FixedSizePool[T >: Null <: AnyRef: ClassTag](
  val capacity: Int,
  val create: () => T
) {
  private val queue = new Array[T](capacity + 1)
  private var start = 0
  private var end = 0

  def acquire(): T = {
    if (start != end) {
      val cur = queue(start)
      queue(start) = null
      start = (start + 1) % capacity
      cur
    } else create()
  }

  def release(x: T): Unit = {
    assert(x != null)
    val nend = (end + 1) % capacity
    if (nend != start) {
      queue(end) = x
      end = nend
    }
  }
}
