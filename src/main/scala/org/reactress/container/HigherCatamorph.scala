package org.reactress
package container



import scala.collection._



class HigherCatamorph[@spec(Int, Long, Double) T](val catamorph: ReactCatamorph[T, Signal[T]])
extends ReactCatamorph[T, Signal[T]] with ReactBuilder[Signal[T], HigherCatamorph[T]] {
  private var subscriptions = mutable.Map[Signal[T], Reactive.Subscription]()
  private var insertsReactive: Reactive[Signal[T]] = null
  private var removesReactive: Reactive[Signal[T]] = null

  def init(c: ReactCatamorph[T, Signal[T]]) {
    insertsReactive = catamorph.inserts
    removesReactive = catamorph.removes
  }

  init(catamorph)

  def builder: ReactBuilder[Signal[T], HigherCatamorph[T]] = this

  def container = this

  def apply(): T = catamorph()
  
  def +=(s: Signal[T]): Boolean = {
    if (catamorph += s) {
      subscriptions(s) = s.onValue { v =>
        catamorph.push(s)
        reactAll(apply())
      }
      reactAll(apply())
      true
    } else false
  }

  def -=(s: Signal[T]): Boolean = {
    if (catamorph -= s) {
      subscriptions.remove(s)
      reactAll(apply())
      true
    } else false
  }

  def push(s: Signal[T]) = catamorph.push(s)

  def inserts: Reactive[Signal[T]] = insertsReactive

  def removes: Reactive[Signal[T]] = removesReactive

  override def toString = s"HigherCatamorph(${apply()})"
}


object HigherCatamorph {

  def monoid[@spec(Int, Long, Double) T](implicit m: Monoid[T]) = {
    val catamorph = new ReactCommutoid[T, Signal[T]](_(), m.zero, m.operator) // TODO separate impl
    new HigherCatamorph[T](catamorph)
  }

  def commutoid[@spec(Int, Long, Double) T](implicit cm: Commutoid[T]) = {
    val catamorph = new ReactCommutoid[T, Signal[T]](_(), cm.zero, cm.operator)
    new HigherCatamorph[T](catamorph)
  }

  def abelian[@spec(Int, Long, Double) T](implicit m: Abelian[T]) = {
    val catamorph = new ReactCommutoid[T, Signal[T]](_(), m.zero, m.operator) // TODO separate impl
    new HigherCatamorph[T](catamorph)
  }

  implicit def factory[@spec(Int, Long, Double) T](implicit m: Monoid[T]) = new ReactBuilder.Factory[Signal[T], HigherCatamorph[T]] {
    def apply() = HigherCatamorph.monoid(m)
  }

  implicit def factory[@spec(Int, Long, Double) T](implicit cm: Commutoid[T]) = new ReactBuilder.Factory[Signal[T], HigherCatamorph[T]] {
    def apply() = HigherCatamorph.commutoid(cm)
  }

  implicit def factory[@spec(Int, Long, Double) T](implicit cm: Abelian[T]) = new ReactBuilder.Factory[Signal[T], HigherCatamorph[T]] {
    def apply() = HigherCatamorph.abelian(cm)
  }

}