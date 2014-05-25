package org.reactress
package container






class EmitContainer[@spec(Int, Long, Double) T]
  (private val foreachF: (T => Unit) => Unit, private val sizeF: () => Int)
extends ReactContainer[T] {
  private[reactress] var insertsEmitter: Reactive.Emitter[T] = null
  private[reactress] var removesEmitter: Reactive.Emitter[T] = null

  private def init(dummy: EmitContainer[T]) {
    insertsEmitter = new Reactive.Emitter[T]
    removesEmitter = new Reactive.Emitter[T]
  }

  init(this)

  def inserts = insertsEmitter

  def removes = removesEmitter

  def foreach(f: T => Unit) = foreachF(f)

  def size = sizeF()

  def react = new ReactContainer.Lifted.Default(this)

}


object EmitContainer {

}
