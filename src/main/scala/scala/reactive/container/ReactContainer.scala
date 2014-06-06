package scala.reactive
package container






trait ReactContainer[@spec(Int, Long, Double) T] extends ReactMutable.Subscriptions {
  self =>

  def inserts: Reactive[T]
  
  def removes: Reactive[T]

  def react: ReactContainer.Lifted[T]

  def foreach(f: T => Unit): Unit

  def size: Int

  def count(p: T => Boolean): Int = {
    var num = 0
    for (v <- this) if (p(v)) num += 1
    num
  }

  def forall(p: T => Boolean): Boolean = count(p) == size

  def exists(p: T => Boolean): Boolean = count(p) > 0

  def fold(m: Monoid[T]): T = {
    var agg = m.zero
    for (v <- this) agg = m.operator(agg, v)
    agg
  }

  def map[@spec(Int, Long, Double) S](f: T => S): ReactContainer[S] =
    new ReactContainer.Map(this, f)

  def filter(p: T => Boolean): ReactContainer[T] =
    new ReactContainer.Filter(this, p)

  def collect[S <: AnyRef](pf: PartialFunction[T, S])(implicit e: T <:< AnyRef): ReactContainer[S] =
    new ReactContainer.Collect(this, pf)

  def union(that: ReactContainer[T])(implicit count: ReactContainer.Union.Count[T], a: Arrayable[T], b: CanBeBuffered): ReactContainer[T] =
    new ReactContainer.Union(this, that, count)

  def to[That <: ReactContainer[T]](implicit factory: ReactBuilder.Factory[T, That]): That = {
    val builder = factory()
    for (x <- this) builder += x
    builder.container
  }

}


object ReactContainer {

  trait Lifted[@spec(Int, Long, Double) T] {
    val container: ReactContainer[T]

    /* queries */

    def size: Signal[Int] with Reactive.Subscription =
      new Size(container)

    def count(p: T => Boolean): Signal[Int] with Reactive.Subscription =
      new Signal.Default[Int] with Reactive.ProxySubscription {
        private[reactive] var value = container.count(p)
        def apply() = value
        val subscription = Reactive.CompositeSubscription(
          container.inserts onEvent { x => if (p(x)) { value += 1; reactAll(value) } },
          container.removes onEvent { x => if (p(x)) { value -= 1; reactAll(value) } }
        )
      }

    def exists(p: T => Boolean): Signal[Boolean] with Reactive.Subscription = count(p).map(_ > 0)

    def forall(p: T => Boolean): Signal[Boolean] with Reactive.Subscription =
      new Signal.Default[Boolean] with Reactive.ProxySubscription {
        private[reactive] var value = container.count(p)
        def apply() = value == container.size
        val subscription = Reactive.CompositeSubscription(
          container.inserts onEvent { x => if (p(x)) value += 1; reactAll(value == container.size) },
          container.removes onEvent { x => if (p(x)) value -= 1; reactAll(value == container.size) }
        )
      }

    def foreach(f: T => Unit): Reactive[Unit] with Reactive.Subscription = {
      container.foreach(f)
      container.inserts.foreach(f)
    }
    
    def monoidFold(implicit m: Monoid[T]): Signal[T] with Reactive.Subscription = new Aggregate(container, MonoidCatamorph[T])

    def commuteFold(implicit c: Commutoid[T]): Signal[T] with Reactive.Subscription = new Aggregate(container, CommuteCatamorph[T])

    def abelianFold(implicit ab: Abelian[T], a: Arrayable[T]): Signal[T] with Reactive.Subscription = new Aggregate(container, AbelianCatamorph[T])

    def mutate(m: ReactMutable)(insert: T => Unit)(remove: T => Unit): Reactive.Subscription = new Mutate(container, insert, remove)

    /* transformers */
  
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

  }

  object Lifted {
    class Default[@spec(Int, Long, Double) T](val container: ReactContainer[T])
    extends Lifted[T]

    class Eager[@spec(Int, Long, Double) T](val container: ReactContainer[T])
    extends Lifted[T] {
      override val size: Signal[Int] with Reactive.Subscription =
        new Signal.Default[Int] with Reactive.ProxySubscription {
          private[reactive] var value = 0
          def apply() = value
          val subscription = Reactive.CompositeSubscription(
            container.inserts on { value += 1; reactAll(value) },
            container.removes on { value -= 1; reactAll(value) }
          )
        }
    }
  }

  trait Default[@spec(Int, Long, Double) T] extends ReactContainer[T] {
    val react = new Lifted.Default[T](this)
  }

  trait Eager[@spec(Int, Long, Double) T] extends ReactContainer[T] {
    val react = new Lifted.Eager[T](this)
  }

  class Size[@spec(Int, Long, Double) T](self: ReactContainer[T])
  extends Signal.Default[Int] with Reactive.ProxySubscription {
    private[reactive] var value = self.size
    def apply() = value
    val subscription = Reactive.CompositeSubscription(
      self.inserts on { value += 1; reactAll(value) },
      self.removes on { value -= 1; reactAll(value) }
    )
  }

  class Mutate[@spec(Int, Long, Double) T](self: ReactContainer[T], insert: T => Unit, remove: T => Unit)
  extends Reactive.ProxySubscription {
    val subscription = Reactive.CompositeSubscription(
      self.inserts.mutate(self)(insert),
      self.removes.mutate(self)(remove)
    )
  }

  class Map[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](self: ReactContainer[T], f: T => S)
  extends ReactContainer.Default[S] {
    val inserts = self.inserts.map(f)
    val removes = self.removes.map(f)
    def size = self.size
    def foreach(g: S => Unit) = self.foreach(x => g(f(x)))
  }

  class Filter[@spec(Int, Long, Double) T](self: ReactContainer[T], p: T => Boolean)
  extends ReactContainer.Default[T] {
    val inserts = self.inserts.filter(p)
    val removes = self.removes.filter(p)
    def size = self.count(p)
    def foreach(f: T => Unit) = self.foreach(x => if (p(x)) f(x))
  }

  class Collect[T, S <: AnyRef](self: ReactContainer[T], pf: PartialFunction[T, S])(implicit e: T <:< AnyRef)
  extends ReactContainer.Default[S] {
    val inserts = self.inserts.collect(pf)
    val removes = self.removes.collect(pf)
    def size = self.count(pf.isDefinedAt)
    def foreach(f: S => Unit) = self.foreach(x => if (pf.isDefinedAt(x)) f(pf(x)))
  }

  class Union[@spec(Int, Long, Double) T]
    (self: ReactContainer[T], that: ReactContainer[T], count: Union.Count[T])(implicit at: Arrayable[T])
  extends ReactContainer.Default[T] {
    val inserts = new Reactive.BindEmitter[T]
    val removes = new Reactive.BindEmitter[T]
    var countSignal: Signal.Mutable[Union.Count[T]] = new Signal.Mutable(count)
    var insertSubscription = (self.inserts union that.inserts).mutate(countSignal, inserts) { x =>
      if (count.inc(x)) inserts += x
    }
    var removeSubscription = (self.removes union that.removes).mutate(countSignal, removes) { x =>
      if (count.dec(x)) removes += x
    }
    def computeUnion = {
      val s = ReactHashSet[T]
      for (v <- self) s += v
      for (v <- that) s += v
      s
    }
    def size = computeUnion.size
    def foreach(f: T => Unit) = computeUnion.foreach(f)
  }

  object Union {
    trait Count[@spec(Int, Long, Double) T] {
      def inc(x: T): Boolean
      def dec(x: T): Boolean
    }

    class PrimitiveCount[@spec(Int, Long, Double) T](implicit val at: Arrayable[T]) extends Count[T] {
      val table = ReactHashValMap[T, Int](at, Arrayable.nonZeroInt)
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
      val table = ReactHashValMap[T, Numeral]
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

  class Aggregate[@spec(Int, Long, Double) T]
    (val container: ReactContainer[T], val catamorph: ReactCatamorph[T, T])
  extends Signal.Proxy[T] with Reactive.ProxySubscription {
    commuted =>
    val id = (v: T) => v
    var subscription: Reactive.Subscription = _
    var proxy: Signal[T] = _

    def init(c: ReactContainer[T]) {
      proxy = catamorph.signal
      for (v <- container) catamorph += v
      subscription = Reactive.CompositeSubscription(
        container.inserts onEvent { v => catamorph += v },
        container.removes onEvent { v => catamorph -= v }
      )
    }

    init(container)
  }

}
