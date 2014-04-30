package org.reactress






trait Enqueuer[@spec(Int, Long, Double) -T] {
  def +=(event: T): Unit
}
