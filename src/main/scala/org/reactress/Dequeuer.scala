package org.reactress






trait Dequeuer[@spec(Int, Long, Double) T] {
  def dequeue(): T
  def isEmpty: Boolean
  def nonEmpty = !isEmpty
}
