package org.reactress
package container






trait ReactContainer[@spec(Int, Long, Double) T, Repr <: ReactContainer[T, Repr]] extends Reactive.MutableSetSubscription {
  self =>

  def inserts: Reactive[T]
  
  def removes: Reactive[T]

  def adjustBuilder[S, That](b: ReactBuilder[S, That]) {}

  // TODO fix - reactAll
  def resizes: Signal[Int] with Reactive.Subscription = {
    new Signal[Int] with Reactive.ProxySubscription {
      private var value = 0
      def apply() = value
      val subscription = Reactive.CompositeSubscription(
        inserts onTick { value += 1 },
        removes onTick { value -= 1 }
      )
    }
  }

  def fold(z: T)(op: (T, T) => T): Signal[T] with Reactive.Subscription = {
    new ReactContainer.Fold(this, z, op)
  }

  def to[That <: ReactContainer[T, That]](implicit factory: ReactBuilder.Factory[_, T, That]): That = {
    val builder = factory(this)
    val result = builder.result

    inserts.update(result) {
      builder += _
    }
    removes.update(result) {
      builder -= _
    }

    result
  }

  def forced[That <: ReactContainer[T, That]](implicit factory: ReactBuilder.Factory[Repr, T, That]): That = to(factory)

  // TODO fix this to val!
  def map[@spec(Int, Long, Double) S, That <: ReactContainer[S, That]](f: T => S)(implicit rs: ReactBuilder.Factory[Repr, S, That]): ReactContainer[S, That] = new ReactContainer[S, That] {
    def inserts = self.inserts.map(f)
    def removes = self.removes.map(f)
  }

  def filter[That <: ReactContainer[T, That]](p: T => Boolean)(implicit rs: ReactBuilder.Factory[Repr, T, That]): ReactContainer[T, That] = new ReactContainer[T, That] {
    def inserts = self.inserts.filter(p)
    def removes = self.removes.filter(p)
  }

  def collect[@spec(Int, Long, Double) S, That <: ReactContainer[S, That]](f: PartialFunction[T, S])(implicit rs: ReactBuilder.Factory[Repr, S, That]): ReactContainer[S, That] = new ReactContainer[S, That] {
    def inserts = self.inserts.collect(f)
    def removes = self.removes.collect(f)
  }

  // TODO ++

}


object ReactContainer {

  class Fold[@spec(Int, Long, Double) T, Repr <: ReactContainer[T, Repr]](val container: ReactContainer[T, Repr], val z: T, val op: (T, T) => T)
  extends Signal[T] with Reactive.ProxySubscription {
    folded =>
    var tree: ReactAggregate.Tree[T, Value[T]] = _
    var subscription: Reactive.Subscription = _

    def init(z: T) {
      tree = new ReactAggregate.Tree[T, Value[T]](z)(op, () => onTick(z), v => Reactive.Subscription.Zero)
      subscription = Reactive.CompositeSubscription(
        container.inserts onValue { v => tree += new Value.Default(v) },
        container.removes onValue { v => tree -= new Value.Default(v) }
      )
    }

    init(z)

    def apply() = tree.root.value

    def onTick(z: T) = reactAll(tree.root.value)
  }


}