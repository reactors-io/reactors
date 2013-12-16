package org.reactress



import collection._



trait Reactive[@spec(Int, Long, Double) T] {
  self =>

  def hasSubscriptions: Boolean

  def onReaction(reactor: Reactor[T]): Reactive.Subscription

  def onValue(reactor: T => Unit): Reactive.Subscription = onReaction(new Reactor[T] {
    def react(value: T) = reactor(value)
    def unreact() {}
  })

  def onTick(reactor: =>Unit): Reactive.Subscription = onReaction(new Reactor[T] {
    def react(value: T) = reactor
    def unreact() {}
  })

  def reactAll(value: T): Unit

  def unreactAll(): Unit

  def foldPast[@spec(Int, Long, Double) S](z: S)(op: (S, T) => S): Signal[S] with Reactive.Subscription = {
    class Fold extends Signal.Default[S] with Reactor[T] with Reactive.ProxySubscription {
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

  def signal(init: T) = foldPast(init) {
    (cached, value) => value
  }

  def asSignalOrElse(init: T) = this match {
    case s: Signal[T] => s
    case _ => this.signal(init)
  }

  def asSignal = this match {
    case s: Signal[T] => s
    case _ => throw new UnsupportedOperationException("This is not a signal.")
  }

  def mutate[M <: ReactMutable](mutable: M)(mutation: (M, T) => Unit): Reactive.Subscription = {
    class Mutate extends Reactor[T] with Reactive.ProxySubscription {
      def react(value: T) = {
        mutation(mutable, value)
        mutable.onMutated()
      }
      def unreact() {}
      val subscription = mutable.bindSubscription(self onReaction this)
    }

    new Mutate
  }

  // TODO union

  // TODO concat

  // TODO after

  // TODO until

  def filter(p: T => Boolean): Reactive[T] with Reactive.Subscription = {
    val rf = new Reactive.Filter[T](self, p)
    rf.subscription = self onReaction rf
    rf
  }

  def map[@spec(Int, Long, Double) S](f: T => S): Reactive[S] with Reactive.Subscription = {
    val rm = new Reactive.Map[T, S](self, f)
    rm.subscription = self onReaction rm
    rm
  }

  /* higher-order combinators */

  def mux[@spec(Int, Long, Double) S](implicit evidence: T <:< Reactive[S]): Reactive[S] = {
    new Reactive.Mux[T, S](this, evidence)
  }

  // TODO union

  // TODO concat

}


object Reactive {

  class Filter[@spec(Int, Long, Double) T](val self: Reactive[T], val p: T => Boolean)
  extends Reactive.Default[T] with Reactor[T] with Reactive.ProxySubscription {
    def react(value: T) {
      if (p(value)) reactAll(value)
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Subscription.empty
  }

  class Map[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](val self: Reactive[T], val f: T => S)
  extends Reactive.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    def react(value: T) {
      reactAll(f(value))
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Subscription.empty
  }

  class Mux[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](val self: Reactive[T], val evidence: T <:< Reactive[S])
  extends Reactive.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    muxed =>
    private[reactress] var currentSubscription: Reactive.Subscription = Reactive.Subscription.empty
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

  trait Never[@spec(Int, Long, Double) T]
  extends Reactive[T] {
    def hasSubscriptions = false
    def onReaction(reactor: Reactor[T]) = {
      reactor.unreact()
      Subscription.empty
    }
    def reactAll(value: T) {}
    def unreactAll() {}
  }

  private object NeverImpl extends Never[Nothing]

  def Never[T] = NeverImpl.asInstanceOf[Reactive[T]]

  // TODO Amb

  trait Subscription {
    def unsubscribe(): Unit
  }

  object Subscription {
    val empty = new Subscription {
      def unsubscribe() {}
    }
  }

  trait SubscriptionSet extends ReactMutable {
    val subscriptions = mutable.Set[Subscription]()

    def clearSubscriptions() {
      for (s <- subscriptions) s.unsubscribe()
      subscriptions.clear()
    }
    
    override def bindSubscription(s: Reactive.Subscription) = new Subscription {
      subscriptions += this
      def unsubscribe() {
        s.unsubscribe()
        subscriptions -= this
      }
    }
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

  trait WeakSource[@spec(Int, Long, Double) T] extends Reactive[T] with WeakHashTable[Reactor[T]] {
    def onReaction(reactor: Reactor[T]) = {
      addEntry(reactor)
      new Subscription {
        def unsubscribe() = removeEntry(reactor)
      }
    }
    def reactAll(value: T) {
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
    def unreactAll() {
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

  trait Default[@spec(Int, Long, Double) T] extends Reactive[T] {
    private val reactors = mutable.ArrayBuffer[Reactor[T]]()
    // private val reactors = new Array[Reactor[T]](8) // TODO fix
    // private var len = 0
    def onReaction(reactor: Reactor[T]) = {
      reactors += reactor
      //reactors(len) = reactor
      //len += 1
      new Subscription {
        //def unsubscribe() = {}
        def unsubscribe() = reactors -= reactor
      }
    }
    def reactAll(value: T) {
      var i = 0
      while (i < reactors.length) {
      //while (i < len) {
        reactors(i).react(value)
        i += 1
      }
    }
    def unreactAll() {
      var i = 0
      while (i < reactors.length) {
      //while (i < len) {
        reactors(i).unreact()
        i += 1
      }
    }
    def hasSubscriptions: Boolean = reactors.size > 0
  }

  class Emitter[@spec(Int, Long, Double) T]
  extends Reactive[T] with Default[T] {
    def +=(value: T) {
      reactAll(value)
    }
  }

}

