package org.reactress
package isolate






trait Enqueuer[@spec(Int, Long, Double) T] {
  def +=(event: T): Unit
}
