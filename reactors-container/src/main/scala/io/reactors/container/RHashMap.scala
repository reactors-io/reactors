package io.reactors
package container



import scala.collection._
import scala.reflect.ClassTag



class RHashMap[@spec(Int, Long, Double) K, V >: Null <: AnyRef](
  implicit val can: RHashMap.Can[K, V], val hash: Hash[K]
) extends RContainer[(K, V)] {
  private var table: Array[RHashMap.Entry[K, V]] = null
  private var elemCount = 0
  private var entryCount = 0
  private[reactors] var insertsEmitter: Events.Emitter[(K, V)] = null
  private[reactors] var removesEmitter: Events.Emitter[(K, V)] = null
  private[reactors] var subscription: Subscription = null

  protected def init(k: K) {
    table = new Array(RHashMap.initSize)
    insertsEmitter = new Events.Emitter[(K, V)]
    removesEmitter = new Events.Emitter[(K, V)]
    subscription = Subscription.empty
  }

  init(null.asInstanceOf[K])

  def inserts: Events[(K, V)] = insertsEmitter

  def removes: Events[(K, V)] = removesEmitter

  def unsubscribe() = subscription.unsubscribe()

  def +=(kv: (K, V)) = {
    insert(kv._1, kv._2)
  }

  def -=(kv: (K, V)) = {
    delete(kv._1, kv._2) != null
  }

  private[reactors] def foreachEntry(f: RHashMap.Entry[K, V] => Unit) {
    var i = 0
    while (i < table.length) {
      var entry = table(i)
      while (entry ne null) {
        f(entry)
        entry = entry.next
      }
      i += 1
    }
  }

  def foreach(f: ((K, V)) => Unit) = foreachEntry {
    e => if (e.value != null) f((e.key, e.value))
  }

  private def lookup(k: K): RHashMap.Entry[K, V] = {
    val pos = index(k)
    var entry = table(pos)

    while (entry != null && entry.key != k) {
      entry = entry.next
    }

    if (entry == null) null
    else entry
  }

  private[reactors] def ensure(k: K): RHashMap.Entry[K, V] = {
    val pos = index(k)
    var entry = table(pos)
    checkResize()

    while (entry != null && entry.key != k) {
      entry = entry.next
    }

    if (entry == null) {
      entry = can.newEntry(k, this)
      entry.next = table(pos)
      table(pos) = entry
      entry
    } else entry
  }

  private[reactors] def clean(entry: RHashMap.Entry[K, V]) {
    if (entry.value == null) {
      val pos = index(entry.key)
      table(pos) = table(pos).remove(entry)
    }
  }

  private[reactors] def insert(k: K, v: V): V = {
    assert(v != null)
    checkResize()

    val pos = index(k)
    var entry = table(pos)

    while (entry != null && entry.key != k) {
      entry = entry.next
    }

    var previousValue: V = null
    if (entry == null) {
      entry = can.newEntry(k, this)
      entry.value = v
      entry.next = table(pos)
      table(pos) = entry
      entryCount += 1
    } else {
      previousValue = entry.value
      entry.value = v
    }

    var needRemoveEvent = false
    if (previousValue == null) elemCount += 1
    else needRemoveEvent = true
    
    {
      if (insertsEmitter.hasSubscriptions)
        insertsEmitter.react((k, v))
      emitRemoves(k, previousValue)
    }
    
    entry.propagate()

    previousValue
  }

  private[reactors] def emitRemoves(k: K, v: V) {
    if (removesEmitter.hasSubscriptions) removesEmitter.react((k, v))
  }

  private[reactors] def delete(k: K, expectedValue: V = null): V = {
    val pos = index(k)
    var entry = table(pos)

    while (entry != null && entry.key != k) {
      entry = entry.next
    }

    if (entry == null) null
    else {
      val previousValue = entry.value

      if (expectedValue == null || expectedValue == previousValue) {
        entry.value = null

        elemCount -= 1
        if (!entry.hasSubscriptions) {
          table(pos) = table(pos).remove(entry)
          entryCount -= 1
        }

        if (removesEmitter.hasSubscriptions)
          removesEmitter.react(k, previousValue)
        entry.propagate()

        previousValue
      } else null
    }
  }

  private def checkResize() {
    if (entryCount * 1000 / RHashMap.loadFactor > table.length) {
      val otable = table
      val ncapacity = table.length * 2
      table = new Array(ncapacity)
      elemCount = 0
      entryCount = 0

      var opos = 0
      while (opos < otable.length) {
        var entry = otable(opos)
        while (entry != null) {
          val nextEntry = entry.next
          val pos = index(entry.key)
          entry.next = table(pos)
          table(pos) = entry
          entryCount += 1
          if (entry.value != null) elemCount += 1
          entry = nextEntry
        }
        opos += 1
      }
    }
  }

  private def index(k: K): Int = {
    val hc = hash(k)
    math.abs(scala.util.hashing.byteswap32(hc)) % table.length
  }

  private def noKeyError(key: K) = throw new NoSuchElementException("key: " + key)

  def nil: V = null

  def applyOrNil(key: K): V = {
    val entry = lookup(key)
    if (entry == null || entry.value == null) null
    else entry.value
  }

  def apply(key: K): V = {
    val entry = lookup(key)
    if (entry == null || entry.value == null) noKeyError(key)
    else entry.value
  }

  def get(key: K): Option[V] = {
    val entry = lookup(key)
    if (entry == null || entry.value == null) None
    else Some(entry.value)
  }

  def contains(key: K): Boolean = {
    val entry = lookup(key)
    if (entry == null || entry.value == null) false
    else true
  }

  def update(key: K, value: V): Unit = {
    insert(key, value)
  }

  def remove(key: K): Boolean = delete(key) match {
    case null => false
    case v => true
  }

  def clear() {
    var pos = 0
    while (pos < table.length) {
      var entry = table(pos)
      while (entry != null) {
        val nextEntry = entry.next
        val previousValue = entry.value

        entry.value = null
        if (!entry.hasSubscriptions) table(pos) = table(pos).remove(entry)
        entry.propagate()
        if (previousValue != null) {
          elemCount -= 1
          emitRemoves(entry.key, previousValue)
        }

        entry = nextEntry
      }

      pos += 1
    }
    if (elemCount != 0) {
      throw new IllegalStateException("Size not zero after clear: " + elemCount)
    }
  }

  def size: Int = elemCount
  
  override def toString = {
    val elemCount = mutable.Buffer[(K, V)]()
    for (kv <- this) elemCount += kv
    s"RHashMap($size, ${elemCount.mkString(", ")})"
  }

}


object RHashMap {

  trait Entry[@spec(Int, Long, Double) K, V >: Null <: AnyRef]
  extends Events.Push[V] {
    def outer: RHashMap[K, V]
    def key: K
    def value: V
    def value_=(v: V): Unit
    def next: Entry[K, V]
    def next_=(e: Entry[K, V]): Unit
    def apply(): V = value
    def propagate() = reactAll(value)
    def remove(e: Entry[K, V]): Entry[K, V] = if (this eq e) next else {
      if (next ne null) next = next.remove(e)
      this
    }
    override def onReaction(obs: Observer[V]): Subscription = {
      val sub = super.onReaction(obs)
      sub.and(if (!hasSubscriptions) outer.clean(this))
    }
    override def toString = s"Entry($key, $value)"
  }

  trait Can[@spec(Int, Long, Double) K, V >: Null <: AnyRef] {
    def newEntry(key: K, outer: RHashMap[K, V]): RHashMap.Entry[K, V]
  }

  implicit def canAnyRef[K, V >: Null <: AnyRef] = new Can[K, V] {
    def newEntry(k: K, o: RHashMap[K, V]) = new RHashMap.Entry[K, V] {
      def outer = o
      def key = k
      var value: V = _
      var next: Entry[K, V] = null
    }
  }

  implicit def canInt[V >: Null <: AnyRef] = new Can[Int, V] {
    def newEntry(k: Int, o: RHashMap[Int, V]) = new RHashMap.Entry[Int, V] {
      def outer = o
      def key = k
      var value: V = _
      var next: Entry[Int, V] = null
    }
  }

  implicit def canLong[V >: Null <: AnyRef] = new Can[Long, V] {
    def newEntry(k: Long, o: RHashMap[Long, V]) = new RHashMap.Entry[Long, V] {
      def outer = o
      def key = k
      var value: V = _
      var next: Entry[Long, V] = null
    }
  }

  implicit def canDouble[V >: Null <: AnyRef] = new Can[Double, V] {
    def newEntry(k: Double, o: RHashMap[Double, V]) = new RHashMap.Entry[Double, V] {
      def outer = o
      def key = k
      var value: V = _
      var next: Entry[Double, V] = null
    }
  }

  def apply[@spec(Int, Long, Double) K, V >: Null <: AnyRef](
    implicit can: Can[K, V], hash: Hash[K]
  ) = {
    new RHashMap[K, V]()(can, hash)
  }

  val initSize = 16

  val loadFactor = 750

  implicit def factory[@spec(Int, Long, Double) K, V >: Null <: AnyRef](
    implicit can: Can[K, V], hash: Hash[K]
  ) = {
    new RContainer.Factory[(K, V), RHashMap[K, V]] {
      def apply(inserts: Events[(K, V)], removes: Events[(K, V)]): RHashMap[K, V] = {
        val hm = new RHashMap[K, V]
        hm.subscription = new Subscription.Composite(
          inserts.onEvent(hm += _),
          removes.onEvent(hm -= _)
        )
        hm
      }
    }
  }

}
