package org.reactress






trait Dequeuer[@spec(Int, Long, Double) T] {
  def dequeue(): T
  def dequeueEnqueueIfEmpty(v: T): T
  def isEmpty: Boolean
  def nonEmpty = !isEmpty
}
