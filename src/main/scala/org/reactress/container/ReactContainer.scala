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
        inserts onTick { value += 1; reactAll(value) },
        removes onTick { value -= 1; reactAll(value) }
      )
    }
  }

  def aggregate(implicit canAggregate: ReactContainer.CanAggregate[T]): Signal[T] with Reactive.Subscription = {
    canAggregate.apply(this)
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

  trait CanAggregate[@spec(Int, Long, Double) T] {
    def apply(c: ReactContainer[T]): Signal[T] with Reactive.Subscription
  }

  implicit def canAggregateMonoid[@spec(Int, Long, Double) T](implicit m: Monoid[T]) = new CanAggregate[T] {
    def apply(c: ReactContainer[T]) = new Aggregate[T](c, m.zero, m.operator)
  }

  implicit def monoidToCanAggregate[@spec(Int, Long, Double) T](m: Monoid[T]) = canAggregateMonoid(m)

  implicit def canAggregateCommutoid[@spec(Int, Long, Double) T](implicit m: Commutoid[T]) = new CanAggregate[T] {
    def apply(c: ReactContainer[T]) = new Commute[T](c, m.zero, m.operator)
  }

  implicit def commutoidToCanAggregate[@spec(Int, Long, Double) T](m: Commutoid[T]) = canAggregateCommutoid(m)

  implicit def canAggregateGroup[@spec(Int, Long, Double) T](implicit m: Group[T]) = new CanAggregate[T] {
    def apply(c: ReactContainer[T]) = new AggregateUndo[T](c, m.zero, m.operator, m.inverse)
  }

  implicit def groupToCanAggregate[@spec(Int, Long, Double) T](g: Group[T]) = canAggregateGroup(g)

  class Aggregate[@spec(Int, Long, Double) T](val container: ReactContainer[T], val z: T, val op: (T, T) => T)
  extends Signal[T] with Reactive.ProxySubscription {
    agregated =>
    var tree: ReactCommuteAggregate.Tree[T, Value[T]] = _ // TODO change with regular aggregate tree
    var subscription: Reactive.Subscription = _

    def init(z: T) {
      tree = new ReactCommuteAggregate.Tree[T, Value[T]](z)(op, () => onTick(z), v => Reactive.Subscription.empty)
      subscription = Reactive.CompositeSubscription(
        container.inserts onValue { v => tree += new Value.Default(v) },
        container.removes onValue { v => tree -= new Value.Default(v) }
      )
    }

    init(z)

    def apply() = tree.root.value

    def onTick(z: T) = reactAll(tree.root.value)
  }

  class Commute[@spec(Int, Long, Double) T](val container: ReactContainer[T], val z: T, val op: (T, T) => T)
  extends Signal[T] with Reactive.ProxySubscription {
    commuted =>
    var tree: ReactCommuteAggregate.Tree[T, Value[T]] = _
    var subscription: Reactive.Subscription = _

    def init(z: T) {
      tree = new ReactCommuteAggregate.Tree[T, Value[T]](z)(op, () => onTick(z), v => Reactive.Subscription.empty)
      subscription = Reactive.CompositeSubscription(
        container.inserts onValue { v => tree += new Value.Default(v) },
        container.removes onValue { v => tree -= new Value.Default(v) }
      )
    }

    init(z)

    def apply() = tree.root.value

    def onTick(z: T) = reactAll(tree.root.value)
  }

  class AggregateUndo[@spec(Int, Long, Double) T](val container: ReactContainer[T], val z: T, val op: (T, T) => T, val inv: (T, T) => T)
  extends Signal[T] with Reactive.ProxySubscription {
    aggregated =>
    private var value = z
    def apply() = value
    val subscription = Reactive.CompositeSubscription(
      container.inserts onValue { x => value = op(value, x) },
      container.removes onValue { x => value = inv(value, x) }
    )
  }

}
