package org.reactress
package container



import scala.collection._



trait ReactStruct extends ReactMutable.SubscriptionSet {
  private val mutableSignals = scala.collection.mutable.Set[Signal.Mutable[_]]()
  def mutable[T <: AnyRef](v: T) = {
    val sm = Signal.Mutable(v)
    mutableSignals += sm
    sm
  }
  override def onMutated() {
    for (s <- mutableSignals) s.onMutated()
  }
}