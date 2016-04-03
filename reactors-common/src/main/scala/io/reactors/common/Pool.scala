package io.reactors



import scala.reflect.ClassTag



abstract class Pool[T >: Null <: AnyRef] {
  def acquire(): T
  def release(x: T): Unit
}


object Pool {
  class Zero[T >: Null <: AnyRef](
    val create: () => T,
    val onAcquire: T => Unit,
    val onRelease: T => Unit
  ) extends Pool[T] {
    def acquire(): T = create()
    def release(x: T): Unit = {}
  }
}
