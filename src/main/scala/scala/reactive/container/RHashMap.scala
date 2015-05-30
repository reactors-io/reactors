package scala.reactive
package container



import scala.collection._
import scala.reflect.ClassTag



class RHashMap[@spec(Int, Long, Double) K, V >: Null <: AnyRef]
  (implicit val can: RHashMap.Can[K, V])
extends RMap[K, V] with RBuilder[(K, V), RHashMap[K, V]] with PairBuilder[K, V, RHashMap[K, V]] {
  private var table: Array[RHashMap.Entry[K, V]] = null
  private var elemCount = 0
  private var entryCount = 0
  private[reactive] var keysContainer: RContainer.Emitter[K] = null
  private[reactive] var valuesContainer: RContainer.Emitter[V] = null
  private[reactive] var entriesContainer: PairContainer.Emitter[K, V] = null
  private[reactive] var insertsEmitter: Reactive.Emitter[(K, V)] = null
  private[reactive] var removesEmitter: Reactive.Emitter[(K, V)] = null


  protected def init(k: K) {
    table = new Array(RHashMap.initSize)
    keysContainer = new RContainer.Emitter[K](f => foreachKey(f), () => size)
    valuesContainer = new RContainer.Emitter[V](f => foreachValue(f), () => size)
    entriesContainer = new PairContainer.Emitter[K, V]()
    insertsEmitter = new Reactive.Emitter[(K, V)]
    removesEmitter = new Reactive.Emitter[(K, V)]
  }

  init(null.asInstanceOf[K])

  def keys: RContainer[K] = keysContainer
  def values: RContainer[V] = valuesContainer
  def inserts: Reactive[(K, V)] = insertsEmitter
  def removes: Reactive[(K, V)] = removesEmitter
  def entries: PairContainer[K, V] = entriesContainer

  def builder: RBuilder[(K, V), RHashMap[K, V]] = this

  def +=(kv: (K, V)) = {
    insertPair(kv._1, kv._2)
  }

  def -=(kv: (K, V)) = {
    removePair(kv._1, kv._2)
  }

  def insertPair(k: K, v: V) = {
    insert(k, v)
    true
  }

  def removePair(k: K, v: V) = {
    delete(k, v) != null
  }

  def container = this

  val react = new RHashMap.Lifted[K, V](this)

  def foreachEntry(f: RHashMap.Entry[K, V] => Unit) {
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

  def foreachKey(f: K => Unit) = foreachEntry {
    e => if (e.value != null) f(e.key)
  }

  def foreachValue(f: V => Unit) = foreachEntry {
    e => if (e.value != null) f(e.value)
  }

  def foreachPair(f: (K, V) => Unit) = foreachEntry {
    e => if (e.value != null) f(e.key, e.value)
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

  private[reactive] def ensure(k: K): RHashMap.Entry[K, V] = {
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

  private[reactive] def clean(entry: RHashMap.Entry[K, V]) {
    if (entry.value == null) {
      val pos = index(entry.key)
      table(pos) = table(pos).remove(entry)
    }
  }

  private def emitRemoves(k: K, previousValue: V) {
    keysContainer.removes.react(k)
    valuesContainer.removes.react(previousValue)
    entriesContainer.removes.react(k, previousValue)
    if (removesEmitter.hasSubscriptions)
      removesEmitter.react((k, previousValue))
  }

  private def insert(k: K, v: V): V = {
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

    if (previousValue == null) elemCount += 1
    else emitRemoves(k, previousValue)
    
    {
      keysContainer.inserts.react(k)
      valuesContainer.inserts.react(v)
      entriesContainer.inserts.react(k, v)
      if (insertsEmitter.hasSubscriptions) insertsEmitter.react((k, v))
    }
    
    entry.propagate()

    previousValue
  }

  private def delete(k: K, expectedValue: V = null): V = {
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

        emitRemoves(k, previousValue)
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
    val hc = k.##
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
  extends Signal.Default[V] {
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
    override def onSubscriptionChange() = if (!hasSubscriptions) outer.clean(this)
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

  def apply[@spec(Int, Long, Double) K, V >: Null <: AnyRef](implicit can: Can[K, V]) = new RHashMap[K, V]()(can)

  class Lifted[@spec(Int, Long, Double) K, V >: Null <: AnyRef](val container: RHashMap[K, V])
  extends RMap.Lifted[K, V] {
    def apply(k: K): Signal[V] = {
      container.ensure(k).signal(container.applyOrNil(k))
    }
    def get(k: K): Signal[Option[V]] = {
      container.ensure(k).signal(container.applyOrNil(k)).map { v =>
        if (v == container.nil) None else Some(v)
      }
    }
  }

  val initSize = 16

  val loadFactor = 750

  implicit def factory[@spec(Int, Long, Double) K, V >: Null <: AnyRef] = new RBuilder.Factory[(K, V), RHashMap[K, V]] {
    def apply() = RHashMap[K, V]
  }

  implicit def pairFactory[@spec(Int, Long, Double) K, V >: Null <: AnyRef] = new PairBuilder.Factory[K, V, RHashMap[K, V]] {
    def apply() = RHashMap[K, V]
  }

}





