package org.reactress



import java.lang.ref.{WeakReference => WeakRef}
import scala.collection._



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

  private val bufferSizeBound = 8
  private val hashTableSizeBound = 5

  trait Default[@spec(Int, Long, Double) T] extends Reactive[T] {
    private[reactress] var demux: AnyRef = null
    def onReaction(reactor: Reactor[T]) = {
      demux match {
        case null =>
          demux = new WeakRef(reactor)
        case w: WeakRef[Reactor[T] @unchecked] =>
          val wb = new WeakBuffer[Reactor[T]]
          wb.addRef(w)
          wb.addEntry(reactor)
          demux = wb
        case wb: WeakBuffer[Reactor[T] @unchecked] =>
          if (wb.size < bufferSizeBound) {
            wb.addEntry(reactor)
          } else {
            val wht = toHashTable(wb)
            wht.addEntry(reactor)
            demux = wht
          }
        case wht: WeakHashTable[Reactor[T] @unchecked] =>
          wht.addEntry(reactor)
      }
      newSubscription(reactor)
    }
    private def newSubscription(r: Reactor[T]) = new Subscription {
      onSubscriptionChange()
      def unsubscribe() = removeReaction(r)
    }
    private def removeReaction(r: Reactor[T]) {
      demux match {
        case null =>
          // nothing to remove
        case w: WeakRef[Reactor[T] @unchecked] =>
          if (w.get eq r) demux = null
        case wb: WeakBuffer[Reactor[T] @unchecked] =>
          wb.removeEntry(r)
        case wht: WeakHashTable[Reactor[T] @unchecked] =>
          wht.removeEntry(r)
      }
      onSubscriptionChange()
    }
    def reactAll(value: T) {
      demux match {
        case null =>
          // no need to inform anybody
        case w: WeakRef[Reactor[T] @unchecked] =>
          val r = w.get
          if (r != null) r.react(value)
          else demux = null
        case wb: WeakBuffer[Reactor[T] @unchecked] =>
          bufferReactAll(wb, value)
        case wht: WeakHashTable[Reactor[T] @unchecked] =>
          tableReactAll(wht, value)
      }
    }
    def unreactAll() {
      demux match {
        case null =>
          // no need to inform anybody
        case w: WeakRef[Reactor[T] @unchecked] =>
          val r = w.get
          if (r != null) r.unreact()
          else demux = null
        case wb: WeakBuffer[Reactor[T] @unchecked] =>
          bufferUnreactAll(wb)
        case wht: WeakHashTable[Reactor[T] @unchecked] =>
          tableUnreactAll(wht)
      }
    }
    private def checkBuffer(wb: WeakBuffer[Reactor[T]]) {
      if (wb.size <= 1) {
        if (wb.size == 1) demux = new WeakRef(wb(0))
        else if (wb.size == 0) demux = null
      }
    }
    private def bufferReactAll(wb: WeakBuffer[Reactor[T]], value: T) {
      val array = wb.array
      var until = wb.size
      var i = 0
      while (i < until) {
        val ref = array(i)
        val r = ref.get
        if (r ne null) {
          r.react(value)
          i += 1
        } else {
          wb.removeEntryAt(i)
          until -= 1
        }
      }
      checkBuffer(wb)
    }
    private def bufferUnreactAll(wb: WeakBuffer[Reactor[T]]) {
      val array = wb.array
      var until = wb.size
      var i = 0
      while (i < until) {
        val ref = array(i)
        val r = ref.get
        if (r ne null) {
          r.unreact()
          i += 1
        } else {
          wb.removeEntryAt(i)
          until -= 1
        }
      }
      checkBuffer(wb)
    }
    private def toHashTable(wb: WeakBuffer[Reactor[T]]) = {
      val wht = new WeakHashTable[Reactor[T]]
      val array = wb.array
      val until = wb.size
      var i = 0
      while (i < until) {
        val r = array(i).get
        if (r != null) wht.addEntry(r)
        i += 1
      }
      wht
    }
    private def toBuffer(wht: WeakHashTable[Reactor[T]]) = {
      val wb = new WeakBuffer[Reactor[T]](bufferSizeBound)
      val table = wht.table
      var i = 0
      while (i < table.length) {
        var ref = table(i)
        if (ref != null && ref.get != null) wb.addRef(ref)
        i += 1
      }
      wb
    }
    private def checkHashTable(wht: WeakHashTable[Reactor[T]]) {
      if (wht.size < hashTableSizeBound) {
        val wb = toBuffer(wht)
        demux = wb
        checkBuffer(wb)
      }
    }
    private def tableReactAll(wht: WeakHashTable[Reactor[T]], value: T) {
      val table = wht.table
      var i = 0
      while (i < table.length) {
        val ref = table(i)
        if (ref ne null) {
          val r = ref.get
          if (r ne null) r.react(value)
          else wht.removeEntryAt(i, null)
        }
        i += 1
      }
      checkHashTable(wht)
    }
    private def tableUnreactAll(wht: WeakHashTable[Reactor[T]]) {
      val table = wht.table
      var i = 0
      while (i < table.length) {
        val ref = table(i)
        if (ref ne null) {
          val r = ref.get
          if (r ne null) r.unreact()
          else wht.removeEntryAt(i, null)
          i += 1
        }
      }
      checkHashTable(wht)
    }
    def hasSubscriptions: Boolean = demux != null
    def onSubscriptionChange() {}
  }

  class Emitter[@spec(Int, Long, Double) T]
  extends Reactive[T] with Default[T] {
    def +=(value: T) {
      reactAll(value)
    }
  }

}

