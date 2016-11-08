package io.reactors
package protocol






trait StandardAbstractions {
  sealed trait Stamp[T] {
    def isEmpty: Boolean
    def nonEmpty: Boolean
    def stamp: Long
  }

  object Stamp {
    case class None[T]() extends Stamp[T] {
      def isEmpty = true
      def nonEmpty = false
      def stamp = -1
    }

    case class Some[T](x: T, stamp: Long) extends Stamp[T] {
      def isEmpty = false
      def nonEmpty = true
    }
  }
}
