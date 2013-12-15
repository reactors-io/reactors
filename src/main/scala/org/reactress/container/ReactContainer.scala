package org.reactress
package container






trait ReactContainer[@spec(Int, Long, Double) T] extends ReactMutable {
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

  def aggregate(z: T)(op: (T, T) => T): Signal[T] with Reactive.Subscription = {
    new ReactContainer.Aggregate(this, z, op)
  }

  def to[That <: ReactContainer[T]](implicit factory: ReactBuilder.Factory[T, That]): That = {
    val builder = factory(this)
    val result = builder.container

    inserts.mutate(builder) {
      _ += _
    }
    removes.mutate(builder) {
      _ -= _
    }

    result
  }

  // TODO fix this to val!
  def map[@spec(Int, Long, Double) S](f: T => S): ReactContainer[S] =
    new ReactContainer.Map[T, S](self, f)

  def filter(p: T => Boolean): ReactContainer[T] =
    new ReactContainer.Filter[T](self, p)

  // TODO union

}


object ReactContainer {

  class Map[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](self: ReactContainer[T], f: T => S)
  extends ReactContainer[S] {
    val inserts = self.inserts.map(f)
    val removes = self.removes.map(f)
  }

  class Filter[@spec(Int, Long, Double) T](self: ReactContainer[T], p: T => Boolean)
  extends ReactContainer[T] {
    val inserts = self.inserts.filter(p)
    val removes = self.removes.filter(p)
  }

  class Aggregate[@spec(Int, Long, Double) T](val container: ReactContainer[T], val z: T, val op: (T, T) => T)
  extends Signal[T] with Reactive.ProxySubscription {
    folded =>
    var tree: ReactAggregate.Tree[T, Value[T]] = _
    var subscription: Reactive.Subscription = _

    def init(z: T) {
      tree = new ReactAggregate.Tree[T, Value[T]](z)(op, () => onTick(z), v => Reactive.Subscription.empty)
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
