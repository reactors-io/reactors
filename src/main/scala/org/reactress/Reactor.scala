package org.reactress






trait Reactor[@spec(Int, Long, Double) T] {
  def react(value: T): Unit
  def unreact(): Unit
}


object Reactor {

}
