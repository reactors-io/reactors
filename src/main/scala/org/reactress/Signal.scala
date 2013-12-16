package org.reactress






trait Signal[@spec(Int, Long, Double) T]
extends Reactive[T] {
  self =>

  def apply(): T

  override def map[@spec(Int, Long, Double) S](f: T => S): Signal[S] with Reactive.Subscription = {
    class Map extends Signal.Default[S] with Reactor[T] with Reactive.ProxySubscription {
      private var cached = f(self.apply)
      def apply() = cached
      def react(value: T) {
        cached = f(value)
        reactAll(cached)
      }
      def unreact() {
        unreactAll()
      }
      val subscription = self onReaction this
    }

    new Map
  }

  def reducePast(op: (T, T) => T): Signal[T] with Reactive.Subscription = {
    val initial = this()
    class Reduce extends Signal.Default[T] with Reactor[T] with Reactive.ProxySubscription {
      private var cached = initial
      def apply() = cached
      def react(value: T) {
        cached = op(cached, value)
        reactAll(cached)
      }
      def unreact() {
        unreactAll()
      }
      val subscription = self onReaction this
    }

    new Reduce
  }

  def renewed: Signal[T] with Reactive.Subscription = signal(apply())

  def changed: Signal[T] with Reactive.Subscription = {
    val initial = this()
    class Changed extends Signal.Default[T] with Reactor[T] with Reactive.ProxySubscription {
      private var cached = initial
      def apply() = cached
      def react(value: T) {
        if (cached != value) cached = value
        reactAll(cached)
      }
      def unreact() {
        unreactAll()
      }
      val subscription = self onReaction this
    }

    new Changed
  }

  // TODO diffs

  def zip[@spec(Int, Long, Double) S, @spec(Int, Long, Double) R](that: Signal[S])(f: (T, S) => R): Signal[R] with Reactive.Subscription = {
    class Zip extends Signal.Default[R] with Reactive.ProxySubscription {
      zipped =>
      private[reactress] var cached = f(self(), that())
      private[reactress] var left = 2
      private[reactress] def unreact() {
        left -= 1
        if (left == 0) zipped.unreactAll()
      }
      private val selfReactor = new Reactor[T] {
        def react(value: T) {
          cached = f(value, that())
          zipped.reactAll(cached)
        }
        def unreact() = zipped.unreact()
      }
      private val thatReactor = new Reactor[S] {
        def react(value: S) {
          cached = f(self(), value)
          zipped.reactAll(cached)
        }
        def unreact() = zipped.unreact()
      }
      def apply() = cached
      val subscription = Reactive.CompositeSubscription(
        self onReaction selfReactor,
        that onReaction thatReactor
      )
    }

    new Zip
  }

}


object Signal {

  trait Default[@spec(Int, Long, Double) T] extends Signal[T] with Reactive.Default[T]

  class Constant[@spec(Int, Long, Double) T](private val value: T)
  extends Signal[T] with Reactive.Never[T] {
    def apply() = value
  }

  def Constant[@spec(Int, Long, Double) T](value: T) = new Constant(value)

  trait Proxy[@spec(Int, Long, Double) T]
  extends Signal[T] {
    val proxy: Signal[T]
    def apply() = proxy()
    def hasSubscriptions = proxy.hasSubscriptions
    def onReaction(r: Reactor[T]) = proxy.onReaction(r)
    def reactAll(v: T) = proxy.reactAll(v)
    def unreactAll() = proxy.unreactAll()
  }

  final class Mutable[T <: AnyRef](private val m: T)
  extends Signal.Default[T] with Reactive.SubscriptionSet {
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