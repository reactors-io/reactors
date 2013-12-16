package org.reactress
package container



import scala.collection._



class ReactAbelian[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
  (val get: S => T, val zero: T, val op: (T, T) => T, val inv: (T, T) => T)
  (implicit val canS: Arrayable[S], val canT: Arrayable[T])
extends ReactCatamorph[T, S] with ReactBuilder[S, ReactAbelian[T, S]] {
  import ReactAbelian._

  private[reactress] var value: T = _
  private[reactress] var elements: mutable.Map[S, T] = null
  private var insertsEmitter: Reactive.Emitter[S] = null
  private var removesEmitter: Reactive.Emitter[S] = null

  def inserts: Reactive[S] = insertsEmitter

  def removes: Reactive[S] = removesEmitter

  def init(z: T) {
    value = zero
    elements = mutable.Map[S, T]()
    insertsEmitter = new Reactive.Emitter[S]
    removesEmitter = new Reactive.Emitter[S]
  }

  init(zero)

  def apply() = value

  def +=(v: S): Boolean = {
    if (!elements.contains(v)) {
      val x = get(v)
      value = op(value, x)
      elements(v) = x
      reactAll(apply())
      insertsEmitter += v
      true
    } else false
  }

  def -=(v: S): Boolean = {
    if (elements.contains(v)) {
      val y = elements(v)
      value = inv(value, y)
      elements.remove(v)
      reactAll(apply())
      removesEmitter += v
      true
    } else false
  }

  def container = this

  def push(v: S): Boolean = {
    if (elements.contains(v)) {
      val y = elements(v)
      val x = get(v)
      value = inv(value, y)
      value = op(value, x)
      elements(v) = x
      reactAll(apply())
      true
    } else false
  }
}


object ReactAbelian {

  def apply[@spec(Int, Long, Double) T](implicit g: Abelian[T], can: Arrayable[T]) = {
    new ReactAbelian[T, T](v => v, g.zero, g.operator, g.inverse)
  }

}