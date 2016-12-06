package io.reactors.protocol



import io.reactors.Arrayable
import io.reactors.RCell
import io.reactors.container.RHashSet
import io.reactors.container.RRing



class MultiValve[T: Arrayable](val window: Int) {
  private val ring = new RRing[T](window)
  private val valves = new RHashSet[Valve[T]]
  private val earliest = RCell(0L)
  val out: Valve[T] = ???
  def +=(v: Valve[T]) = {
    val sub = v.available.is(true) on {

    }
    ???
  }
}
