package scala.reactive
package container



import scala.collection._



class SignalCatamorph[@spec(Int, Long, Double) T](val catamorph: RCatamorph[T, Signal[T]])
extends RCatamorph[T, Signal[T]] with RBuilder[Signal[T], SignalCatamorph[T]] {
  private var signalSubscriptions = mutable.Map[Signal[T], Reactive.Subscription]()
  private var insertsReactive: Reactive[Signal[T]] = null
  private var removesReactive: Reactive[Signal[T]] = null
  private var defaultSignal: Signal.Default[T] = null

  def init(c: RCatamorph[T, Signal[T]]) {
    insertsReactive = catamorph.inserts
    removesReactive = catamorph.removes
    defaultSignal = new Signal.Default[T] {
      def apply() = catamorph.signal()
    }
  }

  init(catamorph)

  def builder: RBuilder[Signal[T], SignalCatamorph[T]] = this

  def container = this

  def signal: Signal[T] = defaultSignal
  
  def +=(s: Signal[T]): Boolean = {
    if (catamorph += s) {
      signalSubscriptions(s) = s.observe(new Reactor[T] {
        def react(v: T) {
          catamorph.push(s)
          defaultSignal.reactAll(catamorph.signal())
        }
        def except(t: Throwable) {
          defaultSignal.exceptAll(t)
        }
        def unreact() {}
      })
      defaultSignal.reactAll(catamorph.signal())
      true
    } else false
  }

  def -=(s: Signal[T]): Boolean = {
    if (catamorph -= s) {
      signalSubscriptions(s).unsubscribe()
      signalSubscriptions.remove(s)
      defaultSignal.reactAll(catamorph.signal())
      true
    } else false
  }

  def push(s: Signal[T]) = catamorph.push(s)

  def inserts: Reactive[Signal[T]] = insertsReactive

  def removes: Reactive[Signal[T]] = removesReactive

  def size = signalSubscriptions.size

  def foreach(f: Signal[T] => Unit) = signalSubscriptions.keys.foreach(f)

  override def toString = s"SignalCatamorph(${signal()})"
}


trait LowLowSignalCatamorph {
  implicit def monoidFactory[@spec(Int, Long, Double) T](implicit m: Monoid[T]) = new RBuilder.Factory[Signal[T], SignalCatamorph[T]] {
    def apply() = SignalCatamorph.monoid
  }
}


trait LowSignalCatamorph extends LowLowSignalCatamorph {
  implicit def commutoidFactory[@spec(Int, Long, Double) T](implicit cm: Commutoid[T]) = new RBuilder.Factory[Signal[T], SignalCatamorph[T]] {
    def apply() = SignalCatamorph.commutoid
  }
}


object SignalCatamorph extends LowSignalCatamorph {

  def monoid[@spec(Int, Long, Double) T](implicit m: Monoid[T]) = {
    val catamorph = new MonoidCatamorph[T, Signal[T]](_(), m.zero, m.operator)
    new SignalCatamorph[T](catamorph)
  }

  def commutoid[@spec(Int, Long, Double) T](implicit cm: Commutoid[T]) = {
    val catamorph = new CommuteCatamorph[T, Signal[T]](_(), cm.zero, cm.operator)
    new SignalCatamorph[T](catamorph)
  }

  def abelian[@spec(Int, Long, Double) T](implicit m: Abelian[T], a: Arrayable[T]) = {
    val catamorph = new AbelianCatamorph[T, Signal[T]](_(), m.zero, m.operator, m.inverse)
    new SignalCatamorph[T](catamorph)
  }

  def apply[@spec(Int, Long, Double) T](m: Monoid[T]) = monoid(m)

  def apply[@spec(Int, Long, Double) T](cm: Commutoid[T]) = commutoid(cm)

  def apply[@spec(Int, Long, Double) T](cm: Abelian[T])(implicit a: Arrayable[T]) = abelian(cm, a)

  implicit def abelianFactory[@spec(Int, Long, Double) T](implicit cm: Abelian[T], a: Arrayable[T]) = new RBuilder.Factory[Signal[T], SignalCatamorph[T]] {
    def apply() = SignalCatamorph.abelian
  }

}