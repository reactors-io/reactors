package org.reactress






trait Signal[@spec(Int, Long, Double) T]
extends Reactive[T] {
  self =>

  def apply(): T

  def past2 = foldPast((this(), this())) {
    (t, x) => (t._2, x)
  }

  override def map[@spec(Int, Long, Double) S](f: T => S): Signal[S] with Reactive.Subscription = {
    val sm = new Signal.Map(self, f)
    sm.subscription = self onReaction sm
    sm
  }

  def reducePast(op: (T, T) => T): Signal[T] with Reactive.Subscription = {
    val initial = this()
    val srp = new Signal.ReducePast(self, initial, op)
    srp.subscription = self onReaction srp
    srp
  }

  def renewed: Signal[T] with Reactive.Subscription = signal(apply())

  def changes: Signal[T] with Reactive.Subscription = {
    val initial = this()
    val sc = new Signal.Changes(self, initial)
    sc.subscription = self onReaction sc
    sc
  }

  def diffPast[@spec(Int, Long, Double) S](z: S)(op: (T, T) => S): Signal[S] with Reactive.Subscription = {
    val initial = this()
    val sd = new Signal.DiffPast(self, initial, z, op)
    sd.subscription = self onReaction sd
    sd
  }

  def zip[@spec(Int, Long, Double) S, @spec(Int, Long, Double) R](that: Signal[S])(f: (T, S) => R): Signal[R] with Reactive.Subscription = {
    val sz = new Signal.Zip(self, that, f)
    sz.subscription = Reactive.CompositeSubscription(
      self onReaction sz.selfReactor,
      that onReaction sz.thatReactor
    )
    sz
  }

}


object Signal {

  class Map[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (val self: Signal[T], val f: T => S)
  extends Signal.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    private var cached = f(self.apply)
    def apply() = cached
    def react(value: T) {
      cached = f(value)
      reactAll(cached)
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Reactive.Subscription.empty
  }

  class ReducePast[@spec(Int, Long, Double) T]
    (val self: Signal[T], initial: T, op: (T, T) => T)
  extends Signal.Default[T] with Reactor[T] with Reactive.ProxySubscription {
    private var cached = initial
    def apply() = cached
    def react(value: T) {
      cached = op(cached, value)
      reactAll(cached)
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Reactive.Subscription.empty
  }

  class Changes[@spec(Int, Long, Double) T]
    (val self: Signal[T], var cached: T)
  extends Signal.Default[T] with Reactor[T] with Reactive.ProxySubscription {
    def apply() = cached
    def react(value: T) {
      if (cached != value) {
        cached = value
        reactAll(cached)
      }
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Reactive.Subscription.empty
  }

  class DiffPast[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (val self: Signal[T], var last: T, var cached: S, val op: (T, T) => S)
  extends Signal.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    def apply() = cached
    def react(value: T) {
      cached = op(value, last)
      last = value
      reactAll(cached)
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Reactive.Subscription.empty
  }

  class Zip[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S, @spec(Int, Long, Double) R]
    (val self: Signal[T], that: Signal[S], f: (T, S) => R)
  extends Signal.Default[R] with Reactive.ProxySubscription {
    zipped =>
    private[reactress] var cached = f(self(), that())
    private[reactress] var left = 2
    private[reactress] def unreact() {
      left -= 1
      if (left == 0) zipped.unreactAll()
    }
    private[reactress] val selfReactor = new Reactor[T] {
      def react(value: T) {
        cached = f(value, that())
        zipped.reactAll(cached)
      }
      def unreact() = zipped.unreact()
    }
    private[reactress] val thatReactor = new Reactor[S] {
      def react(value: S) {
        cached = f(self(), value)
        zipped.reactAll(cached)
      }
      def unreact() = zipped.unreact()
    }
    def apply() = cached
    var subscription = Reactive.Subscription.empty
  }

  trait Default[@spec(Int, Long, Double) T] extends Signal[T] with Reactive.Default[T]

  class Constant[@spec(Int, Long, Double) T](private val value: T)
  extends Signal[T] with Reactive.Never[T] {
    def apply() = value
  }

  def Constant[@spec(Int, Long, Double) T](value: T) = new Constant(value)

  trait Proxy[@spec(Int, Long, Double) T]
  extends Signal[T] {
    def proxy: Signal[T]
    def apply() = proxy()
    def hasSubscriptions = proxy.hasSubscriptions
    def onReaction(r: Reactor[T]) = proxy.onReaction(r)
    def reactAll(v: T) = proxy.reactAll(v)
    def unreactAll() = proxy.unreactAll()
  }

  final class Mutable[T <: AnyRef](private val m: T)
  extends Signal.Default[T] with ReactMutable.SubscriptionSet {
    def apply() = m
    override def onMutated() = reactAll(m)
  }

  def Mutable[T <: AnyRef](v: T) = new Mutable[T](v)

  class Aggregate[@spec(Int, Long, Double) T]
    (private val root: Signal[T] with Reactive.Subscription, private val subscriptions: Seq[Reactive.Subscription])
  extends Signal.Default[T] with Reactive.Subscription {
    def apply() = root()
    def unsubscribe() {
      for (s <- subscriptions) s.unsubscribe()
    }
  }

  def Aggregate[@spec(Int, Long, Double) T](signals: Signal[T]*)(op: (T, T) => T) = {
    require(signals.length > 0)
    val leaves = signals.map(_.renewed)
    var ss = leaves
    while (ss.length != 1) {
      val nextLevel = for (pair <- ss.grouped(2)) yield pair match {
        case Seq(s1, s2) => (s1 zip s2) { (x, y) => op(x, y) }
        case Seq(s) => s
      }
      ss = nextLevel.toBuffer
    }
    val root = ss(0)
    new Aggregate[T](root, leaves)
  }

}