package io.reactors.protocol



import io.reactors.Arrayable
import io.reactors.common.ArrayRing
import io.reactors.container.RHashSet



class MultiValve[T: Arrayable](val window: Int) {
  private val buffer = new ArrayRing[T](window)
  private val valves = new RHashSet[Valve[T]]
  val out: Valve[T] = ???
  def +=(v: Valve[T]) = ???
}
