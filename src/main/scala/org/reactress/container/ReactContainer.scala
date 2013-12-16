package org.reactress
package container






trait ReactContainer[@spec(Int, Long, Double) T] extends ReactMutable {
  self =>

  def inserts: Reactive[T]
  
  def removes: Reactive[T]

  def resizes: Signal[Int] with Reactive.Subscription = {
    new Signal.Default[Int] with Reactive.ProxySubscription {
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
    val builder = factory()
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
    def apply(c: ReactContainer[T]) = new Aggregate[T](c, CataMonoid(m))
  }

  implicit def monoidToCanAggregate[@spec(Int, Long, Double) T](m: Monoid[T]) = canAggregateMonoid(m)

  implicit def canAggregateCommutoid[@spec(Int, Long, Double) T](implicit m: Commutoid[T]) = new CanAggregate[T] {
    def apply(c: ReactContainer[T]) = new Aggregate[T](c, CataCommutoid[T])
  }

  implicit def commutoidToCanAggregate[@spec(Int, Long, Double) T](m: Commutoid[T]) = canAggregateCommutoid(m)

  implicit def canAggregateAbelian[@spec(Int, Long, Double) T](implicit m: Abelian[T], can: Arrayable[T]) = new CanAggregate[T] {
    def apply(c: ReactContainer[T]) = new Aggregate[T](c, CataBelian[T])
  }

  implicit def abelianToCanAggregate[@spec(Int, Long, Double) T](g: Abelian[T])(implicit can: Arrayable[T]) =
    canAggregateAbelian(g, can)

  class Aggregate[@spec(Int, Long, Double) T]
    (val container: ReactContainer[T], val proxy: ReactCatamorph[T, T])
  extends Signal.Proxy[T] with Reactive.ProxySubscription {
    commuted =>
    val id = (v: T) => v
    var subscription: Reactive.Subscription = _

    def init(c: ReactContainer[T]) {
      subscription = Reactive.CompositeSubscription(
        container.inserts onValue { v => proxy += v },
        container.removes onValue { v => proxy -= v }
      )
    }

    init(container)
  }

}
