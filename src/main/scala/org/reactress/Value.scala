package org.reactress






trait Value[@spec(Int, Long, Double) T] {
  def apply(): T
}


object Value {
  case class Default[@spec(Int, Long, Double) T](value: T) extends Value[T] {
    def apply() = value
  }
}

