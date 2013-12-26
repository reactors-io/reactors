package org.reactress
package container






trait ReactContainer[@spec(Int, Long, Double) T] extends ReactMutable.SubscriptionSet {
  self =>

  def inserts: Reactive[T]
  
  def removes: Reactive[T]

  def react: ReactContainer.Lifted[T]

}


object ReactContainer {

  trait Lifted[@spec(Int, Long, Double) T] {
    val container: ReactContainer[T]

    def size: Signal[Int] with Reactive.Subscription =
      new Signal.Default[Int] with Reactive.ProxySubscription {
        private var value = 0
        def apply() = value
        val subscription = Reactive.CompositeSubscription(
          container.inserts onEvent { value += 1; reactAll(value) },
          container.removes onEvent { value -= 1; reactAll(value) }
        )
      }

    def foreach[@spec(Int, Long, Double) U](f: T => U): Reactive[Unit] with Reactive.Subscription = {
      container.inserts.foreach(f)
    }
    
    def aggregate(implicit canAggregate: ReactContainer.CanAggregate[T]): Signal[T] with Reactive.Subscription = {
      canAggregate.apply(container)
    }
  
    def to[That <: ReactContainer[T]](implicit factory: ReactBuilder.Factory[T, That]): That = {
      val builder = factory()
      val result = builder.container
  
      container.inserts.mutate(builder) {
        builder += _
      }
      container.removes.mutate(builder) {
        builder -= _
      }
  
      result
    }
  
    def map[@spec(Int, Long, Double) S](f: T => S): ReactContainer.Lifted[S] =
      (new ReactContainer.Map[T, S](container, f)).react
  
    def filter(p: T => Boolean): ReactContainer.Lifted[T] =
      (new ReactContainer.Filter[T](container, p)).react
  
    def union(that: ReactContainer[T])(implicit count: Union.Count[T], b: CanBeBuffered): ReactContainer.Lifted[T] =
      (new ReactContainer.Union[T](container, that, count)).react

  }

  object Lifted {
    class Default[@spec(Int, Long, Double) T](val container: ReactContainer[T])
    extends Lifted[T]
  }

  trait Default[@spec(Int, Long, Double) T] extends ReactContainer[T] {
    val react = new Lifted.Default[T](this)
  }

  class Map[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](self: ReactContainer[T], f: T => S)
  extends ReactContainer.Default[S] {
    val inserts = self.inserts.map(f)
    val removes = self.removes.map(f)
  }

  class Filter[@spec(Int, Long, Double) T](self: ReactContainer[T], p: T => Boolean)
  extends ReactContainer.Default[T] {
    val inserts = self.inserts.filter(p)
    val removes = self.removes.filter(p)
  }

  class Union[@spec(Int, Long, Double) T](self: ReactContainer[T], that: ReactContainer[T], count: Union.Count[T])
  extends ReactContainer.Default[T] {
    val inserts = new Reactive.Emitter[T]
    val removes = new Reactive.Emitter[T]
    var countSignal: Signal.Mutable[Union.Count[T]] = new Signal.Mutable(count)
    var insertUnion = (self.inserts union that.inserts).mutate(countSignal) { x =>
      if (count.inc(x)) inserts += x
    }
    var removeUnion = (self.removes union that.removes).mutate(countSignal) { x =>
      if (count.dec(x)) removes += x
    }
  }

  object Union {
    trait Count[@spec(Int, Long, Double) T] {
      def inc(x: T): Boolean
      def dec(x: T): Boolean
    }

    class PrimitiveCount[@spec(Int, Long, Double) T](implicit val at: Arrayable[T]) extends Count[T] {
      val table = ReactTable[T, Int](at, Arrayable.nonZeroInt)
      def inc(x: T) = {
        val curr = table.applyOrNil(x)
        table(x) = curr + 1
        if (curr == 0) true
        else false
      }
      def dec(x: T) = {
        val curr = table.applyOrNil(x)
        if (curr <= 1) {
          table.remove(x)
          true
        } else {
          table(x) = curr - 1
          false
        }
      }
    }

    sealed trait Numeral {
      def inc: Numeral
      def dec: Numeral
    }
    object Zero extends Numeral {
      def inc = One
      def dec = Zero
    }
    object One extends Numeral {
      def inc = Two
      def dec = Zero
    }
    object Two extends Numeral {
      def inc = Two
      def dec = One
    }

    class RefCount[T](implicit val at: Arrayable[T]) extends Count[T] {
      val table = ReactTable[T, Numeral]
      def inc(x: T) = {
        val curr = table.applyOrElse(x, Zero)
        table(x) = curr.inc
        if (curr == Zero) true
        else false
      }
      def dec(x: T) = {
        val curr = table.applyOrElse(x, Zero)
        val next = curr.dec
        if (next == Zero) {
          table.remove(x)
          true
        } else {
          table(x) = next
          false
        }
      }
    }

    implicit def intCount = new PrimitiveCount[Int]

    implicit def longCount = new PrimitiveCount[Long]

    implicit def doubleCount = new PrimitiveCount[Double]

    implicit def refCount[T >: Null <: AnyRef](implicit at: Arrayable[T]) = new RefCount[T]

  }

  trait CanAggregate[@spec(Int, Long, Double) T] {
    def apply(c: ReactContainer[T]): Signal[T] with Reactive.Subscription
  }

  implicit def canAggregateMonoid[@spec(Int, Long, Double) T](implicit m: Monoid[T]) = new CanAggregate[T] {
    def apply(c: ReactContainer[T]) = new Aggregate[T](c, CataMonoid[T])
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
