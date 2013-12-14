package org.reactress



import collection._



trait Reactive[@spec(Int, Long, Double) T] {
  self =>

  private[reactress] def hasSubscriptions: Boolean

  def onReaction(reactor: Reactor[T]): Reactive.Subscription

  def onValue(reactor: T => Unit): Reactive.Subscription = onReaction(new Reactor[T] {
    def react(value: T) = reactor(value)
    def unreact() {}
  })

  def onTick(reactor: =>Unit): Reactive.Subscription = onReaction(new Reactor[T] {
    def react(value: T) = reactor
    def unreact() {}
  })

  protected def reactAll(value: T): Unit

  protected def unreactAll(): Unit

  def foldPast[@spec(Int, Long, Double) S](z: S)(op: (S, T) => S): Signal[S] with Reactive.Subscription = {
    class Fold extends Signal[S] with Reactor[T] with Reactive.ProxySubscription {
      private var cached = z
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

    new Fold
  }

  def mutation[S](z: S)(op: (S, T) => Unit): Signal[S] with Reactive.Subscription = foldPast(z) { (_, value) =>
    op(z, value)
    z
  }

  def signal(z: T) = foldPast(z) {
    (cached, value) => value
  }

  def update[M <: Reactive.MutableSetSubscription](mutable: M)(f: T => Unit): Signal[M] with Reactive.Subscription = {
    class Update extends Signal[M] with Reactor[T] with Reactive.ProxySubscription {
      def apply() = mutable
      def react(value: T) {
        f(value)
      }
      def unreact() {
      }
      val subscription = self onReaction this
      mutable add subscription
    }

    new Update
  }

  def filter(p: T => Boolean): Reactive[T] with Reactive.Subscription = {
    new Reactive.Default[T] with Reactor[T] with Reactive.ProxySubscription {
      def react(value: T) {
        if (p(value)) reactAll(value)
      }
      def unreact() {
        unreactAll()
      }
      val subscription = self onReaction this
    }
  }

  def map[@spec(Int, Long, Double) S](f: T => S): Reactive[S] with Reactive.Subscription = {
    class Map extends Reactive.Default[S] with Reactor[T] with Reactive.ProxySubscription {
      def react(value: T) {
        reactAll(f(value))
      }
      def unreact() {
        unreactAll()
      }
      val subscription = self onReaction this
    }

    new Map
  }

  def collect[@spec(Int, Long, Double) S](pf: PartialFunction[T, S]): Reactive[S] with Reactive.Subscription = {
    class Collect extends Reactive.Default[S] with Reactor[T] with Reactive.ProxySubscription {
      val collector = pf.runWith(v => reactAll(v))
      def react(value: T) {
        collector(value)
      }
      def unreact() {
        unreactAll()
      }
      val subscription = self onReaction this
    }

    new Collect
  }

  def mux[@spec(Int, Long, Double) S](implicit evidence: T <:< Reactive[S]): Reactive[S] = {
    new Reactive.Mux[T, S](this, evidence)
  }

}


object Reactive {

  class Mux[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](val self: Reactive[T], val evidence: T <:< Reactive[S])
  extends Reactive.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    muxed =>
    private[reactress] var currentSubscription: Reactive.Subscription = Reactive.Subscription.Zero
    private[reactress] var currentTerminated = true
    private[reactress] var terminated = false
    def checkTerminate() = if (terminated && currentTerminated) muxed.unreactAll()
    val reactor = new Reactor[S] {
      def react(value: S) {
        muxed.reactAll(value)
      }
      def unreact() {
        currentTerminated = true
        checkTerminate()
      }
    }
    def react(value: T) {
      val nextReactive = evidence(value)
      currentTerminated = false
      currentSubscription.unsubscribe()
      currentSubscription = nextReactive onReaction reactor
    }
    def unreact() {
      terminated = true
      checkTerminate()
    }
    val subscription = self onReaction this
  }

  private object NeverImpl extends Reactive[Nothing] {
    def hasSubscriptions = false
    def onReaction(reactor: Reactor[Nothing]) = new Subscription {
      def unsubscribe() {}
    }
    protected def reactAll(value: Nothing) {}
    protected def unreactAll() {}
  }

  def Never[T] = NeverImpl.asInstanceOf[Reactive[T]]

  trait Subscription {
    def unsubscribe(): Unit
  }

  object Subscription {
    val Zero = new Subscription {
      def unsubscribe() {}
    }
  }

  trait MutableSetSubscription extends Subscription {
    val subscriptions = mutable.Set[Subscription]()

    def unsubscribe() {
      for (s <- subscriptions) s.unsubscribe()
      subscriptions.clear()
    }

    def add(s: Subscription) = subscriptions += s
  }

  trait ProxySubscription extends Subscription {
    def subscription: Subscription
    def unsubscribe() {
      subscription.unsubscribe()
    }
  }

  def CompositeSubscription(ss: Subscription*): Subscription = new Subscription {
    def unsubscribe() {
      for (s <- ss) s.unsubscribe()
    }
  }

  trait Source[T] extends Reactive[T] {
    private val reactors = mutable.ArrayBuffer[Reactor[T]]()
    def onReaction(reactor: Reactor[T]) = {
      reactors += reactor
      new Subscription {
        def unsubscribe() = reactors -= reactor
      }
    }
    protected def reactAll(value: T) {
      var i = 0
      while (i < reactors.length) {
        reactors(i).react(value)
        i += 1
      }
    }
    protected def unreactAll() {
      var i = 0
      while (i < reactors.length) {
        reactors(i).unreact()
        i += 1
      }
    }
    def hasSubscriptions: Boolean = reactors.size > 0
  }

  trait WeakSource[T] extends Reactive[T] with WeakHashTable[Reactor[T]] {
    def onReaction(reactor: Reactor[T]) = {
      addEntry(reactor)
      new Subscription {
        def unsubscribe() = removeEntry(reactor)
      }
    }
    protected def reactAll(value: T) {
      var i = 0
      while (i < table.length) {
        val ref = table(i)
        if (ref ne null) {
          val r = ref.get
          if (r ne null) r.react(value)
        }
        i += 1
      }
    }
    protected def unreactAll() {
      var i = 0
      while (i < table.length) {
        val ref = table(i)
        if (ref ne null) {
          val r = ref.get
          if (r ne null) r.unreact()
          i += 1
        } else {
          removeEntryAt(i, null)
        }
      }
    }
    def hasSubscriptions = size > 0
  }

  trait StandardSource[T] extends Source[T] {
    val weak: Reactive[T] = new Reactive[T] with WeakSource[T]

    override protected def reactAll(value: T) {
      super.reactAll(value)
      weak.reactAll(value)
    }

    override protected def unreactAll() {
      super.unreactAll()
      weak.unreactAll()
    }

    override def hasSubscriptions = super.hasSubscriptions || weak.hasSubscriptions
  }

  trait Default[@spec(Int, Long, Double) T]
  extends Reactive[T] with StandardSource[T]

  class Emitter[@spec(Int, Long, Double) T]
  extends Reactive[T] with StandardSource[T] {
    def +=(value: T) {
      reactAll(value)
    }
  }

}

