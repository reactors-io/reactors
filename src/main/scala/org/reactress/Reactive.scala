package org.reactress



import scala.annotation.tailrec
import scala.collection._
import util._



trait Reactive[@spec(Int, Long, Double) T] {
  self =>

  def hasSubscriptions: Boolean

  def onReaction(reactor: Reactor[T]): Reactive.Subscription

  def onValue(reactor: T => Unit): Reactive.Subscription = onReaction(new Reactor[T] {
    def react(value: T) = reactor(value)
    def unreact() {}
  })

  def onEvent(reactor: =>Unit): Reactive.Subscription = onReaction(new Reactor[T] {
    def react(value: T) = reactor
    def unreact() {}
  })

  def reactAll(value: T): Unit

  def unreactAll(): Unit

  def foreach(f: T => Unit): Reactive[Unit] with Reactive.Subscription = {
    val rf = new Reactive.Foreach(self, f)
    rf.subscription = self onReaction rf
    rf
  }

  def foldPast[@spec(Int, Long, Double) S](z: S)(op: (S, T) => S): Signal[S] with Reactive.Subscription = {
    new Reactive.FoldPast(self, z, op)
  }

  def signal(init: T) = foldPast(init) {
    (cached, value) => value
  }

  def isSignal: Boolean = this match {
    case s: Signal[T] => true
    case _ => false
  }

  def asSignalOrElse(init: T) = this match {
    case s: Signal[T] => s
    case _ => this.signal(init)
  }

  def asSignal = this match {
    case s: Signal[T] => s
    case _ => throw new UnsupportedOperationException("This is not a signal.")
  }

  def mutate[M <: ReactMutable](mutable: M)(mutation: T => Unit): Reactive.Subscription = {
    val rm = new Reactive.Mutate(self, mutable, mutation)
    rm.subscription = mutable.bindSubscription(self onReaction rm)
    rm
  }

  def after[@spec(Int, Long, Double) S](that: Reactive[S]): Reactive[T] with Reactive.Subscription = {
    val ra = new Reactive.After(self, that)
    ra.selfSubscription = self onReaction ra.selfReactor
    ra.thatSubscription = that onReaction ra.thatReactor
    ra.subscription = Reactive.CompositeSubscription(ra.selfSubscription, ra.thatSubscription)
    ra
  }

  def until[@spec(Int, Long, Double) S](that: Reactive[S]): Reactive[T] with Reactive.Subscription = {
    val ru = new Reactive.Until(self, that)
    ru.selfSubscription = self onReaction ru.selfReactor
    ru.thatSubscription = that onReaction ru.thatReactor
    ru.subscription = Reactive.CompositeSubscription(ru.selfSubscription, ru.thatSubscription)
    ru
  }

  def union(that: Reactive[T]): Reactive[T] with Reactive.Subscription = {
    val ru = new Reactive.Union(self, that)
    ru.selfSubscription = self onReaction ru.selfReactor
    ru.thatSubscription = that onReaction ru.thatReactor
    ru.subscription = Reactive.CompositeSubscription(ru.selfSubscription, ru.thatSubscription)
    ru
  }

  def concat(that: Reactive[T])(implicit a: Arrayable[T], b: CanBeBuffered): Reactive[T] with Reactive.Subscription = {
    val rc = new Reactive.Concat(self, that, a)
    rc.selfSubscription = self onReaction rc.selfReactor
    rc.thatSubscription = that onReaction rc.thatReactor
    rc.subscription = Reactive.CompositeSubscription(rc.selfSubscription, rc.thatSubscription)
    rc
  }

  def sync[@spec(Int, Long, Double) S, @spec(Int, Long, Double) R](that: Reactive[S])(f: (T, S) => R)
    (implicit at: Arrayable[T], as: Arrayable[S], b: CanBeBuffered): Reactive[R] with Reactive.Subscription = {
    val rs = new Reactive.Sync(self, that, f, at, as)
    rs.selfSubscription = self onReaction rs.selfReactor
    rs.thatSubscription = that onReaction rs.thatReactor
    rs.subscription = Reactive.CompositeSubscription(rs.selfSubscription, rs.thatSubscription)
    rs
  }

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

  def mux[@spec(Int, Long, Double) S]()(implicit evidence: T <:< Reactive[S]): Reactive[S] = {
    new Reactive.Mux[T, S](this, evidence)
  }

  def union[@spec(Int, Long, Double) S]()(implicit evidence: T <:< Reactive[S]): Reactive[S] = {
    new Reactive.PostfixUnion[T, S](this, evidence)
  }

  def concat[@spec(Int, Long, Double) S]()(implicit evidence: T <:< Reactive[S], a: Arrayable[S], b: CanBeBuffered): Reactive[S] = {
    val pc = new Reactive.PostfixConcat[T, S](this, evidence)
    pc.subscription = self onReaction pc
    pc
  }

}


object Reactive {

  class Foreach[@spec(Int, Long, Double) T]
    (val self: Reactive[T], val f: T => Unit)
  extends Reactive.Default[Unit] with Reactor[T] with Reactive.ProxySubscription {
    def react(value: T) {
      f(value)
      reactAll(())
    }
    def unreact() {
      unreactAll()
    }
    var subscription = Subscription.empty
  }

  class FoldPast[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (val self: Reactive[T], val z: S, val op: (S, T) => S)
  extends Signal.Default[S] with Reactor[T] with Reactive.ProxySubscription {
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

  class Mutate[@spec(Int, Long, Double) T, M <: ReactMutable]
    (val self: Reactive[T], val mutable: M, val mutation: T => Unit)
  extends Reactor[T] with Reactive.ProxySubscription {
    def react(value: T) = {
      mutation(value)
      mutable.onMutated()
    }
    def unreact() {}
    var subscription = Subscription.empty
  }

  class After[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (val self: Reactive[T], val that: Reactive[S])
  extends Reactive.Default[T] with Reactive.ProxySubscription {
    var started = false
    var live = true
    def unreactBoth() = if (live) {
      live = false
      unreactAll()
    }
    val selfReactor = new Reactor[T] {
      def react(value: T) {
        if (started) reactAll(value)
      }
      def unreact() = unreactBoth()
    }
    val thatReactor = new Reactor[S] {
      def react(value: S) {
        started = true
      }
      def unreact() = unreactBoth()
    }
    var selfSubscription = Subscription.empty
    var thatSubscription = Subscription.empty
    var subscription = Subscription.empty
  }

  class Until[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
    (val self: Reactive[T], val that: Reactive[S])
  extends Reactive.Default[T] with Reactive.ProxySubscription {
    var live = true
    def unreactBoth() = if (live) {
      live = false
      unreactAll()
    }
    val selfReactor = new Reactor[T] {
      def react(value: T) = if (live) reactAll(value)
      def unreact() = unreactBoth()
    }
    val thatReactor = new Reactor[S] {
      def react(value: S) = unreactBoth()
      def unreact() {}
    }
    var selfSubscription: Reactive.Subscription = Subscription.empty
    var thatSubscription: Reactive.Subscription = Subscription.empty
    var subscription = Subscription.empty
  }

  class Union[@spec(Int, Long, Double) T](val self: Reactive[T], val that: Reactive[T])
  extends Reactive.Default[T] with Reactive.ProxySubscription {
    var live = 2
    def unreactBoth() = {
      live -= 1
      if (live == 0) unreactAll()
    }
    val selfReactor = new Reactor[T] {
      def react(value: T) = reactAll(value)
      def unreact() = unreactBoth()
    }
    val thatReactor = new Reactor[T] {
      def react(value: T) = reactAll(value)
      def unreact() = unreactBoth()
    }
    var selfSubscription: Reactive.Subscription = Subscription.empty
    var thatSubscription: Reactive.Subscription = Subscription.empty
    var subscription = Subscription.empty
  }

  class Concat[@spec(Int, Long, Double) T](val self: Reactive[T], val that: Reactive[T], val a: Arrayable[T])
  extends Reactive.Default[T] with Reactive.ProxySubscription {
    var selfLive = true
    var thatLive = true
    val buffer = new UnrolledBuffer[T]()(a)
    def unreactBoth() {
      if (!selfLive && !thatLive) {
        unreactAll()
      }
    }
    def flush() {
      buffer.foreach(v => reactAll(v))
      buffer.clear()
    }
    val selfReactor = new Reactor[T] {
      def react(value: T) = reactAll(value)
      def unreact() {
        selfLive = false
        flush()
        unreactBoth()
      }
    }
    val thatReactor = new Reactor[T] {
      def react(value: T) = {
        if (selfLive) buffer += value
        else reactAll(value)
      }
      def unreact() {
        thatLive = false
        unreactBoth()
      }
    }
    var selfSubscription: Reactive.Subscription = Subscription.empty
    var thatSubscription: Reactive.Subscription = Subscription.empty
    var subscription = Subscription.empty
  }

  class Sync[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S, @spec(Int, Long, Double) R]
    (val self: Reactive[T], val that: Reactive[S], val f: (T, S) => R, val at: Arrayable[T], val as: Arrayable[S])
  extends Reactive.Default[R] with Reactive.ProxySubscription {
    val tbuffer = new UnrolledBuffer[T]()(at)
    val sbuffer = new UnrolledBuffer[S]()(as)
    def unreactBoth() {
      tbuffer.clear()
      sbuffer.clear()
      unreactAll()
    }
    val selfReactor = new Reactor[T] {
      def react(tvalue: T) {
        if (sbuffer.isEmpty) tbuffer += tvalue
        else {
          val svalue = sbuffer.dequeue()
          reactAll(f(tvalue, svalue))
        }
      }
      def unreact() = unreactBoth()
    }
    val thatReactor = new Reactor[S] {
      def react(svalue: S) = {
        if (tbuffer.isEmpty) sbuffer += svalue
        else {
          val tvalue = tbuffer.dequeue()
          reactAll(f(tvalue, svalue))
        }
      }
      def unreact() = unreactBoth()
    }
    var selfSubscription: Reactive.Subscription = Subscription.empty
    var thatSubscription: Reactive.Subscription = Subscription.empty
    var subscription = Subscription.empty
  }

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

  class Mux[T, @spec(Int, Long, Double) S]
    (val self: Reactive[T], val evidence: T <:< Reactive[S])
  extends Reactive.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    muxed =>
    private[reactress] var currentSubscription = Subscription.empty
    private[reactress] var terminated = false
    def checkUnreact() = if (terminated && currentSubscription == Subscription.empty) unreactAll()
    def newReactor() = new Reactor[S] {
      def react(value: S) = reactAll(value)
      def unreact() {
        currentSubscription = Reactive.Subscription.empty
        checkUnreact()
      }
    }
    def react(value: T) {
      val nextReactive = evidence(value)
      currentSubscription.unsubscribe()
      currentSubscription = nextReactive onReaction newReactor()
    }
    def unreact() {
      terminated = true
      checkUnreact()
    }
    override def unsubscribe() {
      currentSubscription.unsubscribe()
      currentSubscription = Subscription.empty
      super.unsubscribe()
    }
    val subscription = self onReaction this
  }

  class PostfixUnion[T, @spec(Int, Long, Double) S]
    (val self: Reactive[T], val evidence: T <:< Reactive[S])
  extends Reactive.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    union =>
    private[reactress] var subscriptions = mutable.Map[Reactive[S], Subscription]()
    private[reactress] var terminated = false
    def checkUnreact() = if (terminated && subscriptions.isEmpty) unreactAll()
    def newReactor(r: Reactive[S]) = new Reactor[S] {
      def react(value: S) = reactAll(value)
      def unreact() = {
        subscriptions.remove(r)
        checkUnreact()
      }
    }
    def react(value: T) {
      val nextReactive = evidence(value)
      if (!subscriptions.contains(nextReactive)) {
        val sub = nextReactive onReaction newReactor(nextReactive)
        subscriptions(nextReactive) = sub
      }
    }
    def unreact() {
      terminated = true
      checkUnreact()
    }
    override def unsubscribe() {
      for (kv <- subscriptions) kv._2.unsubscribe()
      subscriptions.clear()
      super.unsubscribe()
    }
    val subscription = self onReaction this
  }

  final class ConcatEntry[S](var subscription: Subscription, var buffer: UnrolledBuffer[S], var live: Boolean) {
    def ready = buffer == null
  }

  class PostfixConcat[T, @spec(Int, Long, Double) S]
    (val self: Reactive[T], val evidence: T <:< Reactive[S])(implicit val arrayable: Arrayable[S])
  extends Reactive.Default[S] with Reactor[T] with Reactive.ProxySubscription {
    union =>
    private[reactress] var subscriptions = new UnrolledBuffer[ConcatEntry[S]]()
    private[reactress] var terminated = false
    private def checkUnreact() = if (terminated && subscriptions.isEmpty) unreactAll()
    def moveToNext() {
      if (subscriptions.isEmpty) checkUnreact()
      else {
        val entry = subscriptions.head
        val buffer = entry.buffer
        entry.buffer = null
        while (buffer.nonEmpty) reactAll(buffer.dequeue())
        if (!entry.live) {
          subscriptions.dequeue()
          moveToNext()
        }
      }
    }
    def newReactor(entry: ConcatEntry[S]) = new Reactor[S] {
      def react(value: S) {
        if (entry.ready) reactAll(value)
        else entry.buffer.enqueue(value)
      }
      def unreact() {
        if (entry.ready) {
          subscriptions.dequeue()
          moveToNext()
        } else entry.live = false
      }
    }
    def react(value: T) {
      val nextReactive = evidence(value)
      val entry = new ConcatEntry(null, new UnrolledBuffer[S], true)
      entry.subscription = nextReactive onReaction newReactor(entry)
      subscriptions.enqueue(entry)
      if (!subscriptions.head.ready) moveToNext()
    }
    def unreact() {
      terminated = true
      checkUnreact()
    }
    override def unsubscribe() {
      while (subscriptions.nonEmpty) {
        subscriptions.head.subscription.unsubscribe()
        subscriptions.dequeue()
      }
      super.unsubscribe()
    }
    var subscription = Subscription.empty
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
    private[reactress] var unreacted: Boolean = false
    def onReaction(reactor: Reactor[T]) = {
      if (unreacted) {
        reactor.unreact()
        Subscription.empty
      } else {
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
    }
    private def newSubscription(r: Reactor[T]) = new Subscription {
      onSubscriptionChange()
      def unsubscribe() {
        removeReaction(r)
      }
    }
    private def removeReaction(r: Reactor[T]) {
      demux match {
        case null =>
          // nothing to invalidate
        case w: WeakRef[Reactor[T] @unchecked] =>
          if (w.get eq r) w.clear()
        case wb: WeakBuffer[Reactor[T] @unchecked] =>
          wb.invalidateEntry(r)
        case wht: WeakHashTable[Reactor[T] @unchecked] =>
          wht.invalidateEntry(r)
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
      unreacted = true
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

