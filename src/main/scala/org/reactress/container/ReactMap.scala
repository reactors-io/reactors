package org.reactress
package container



import scala.collection._
import scala.reflect.ClassTag



class ReactMap[@spec(Int, Long, Double) K, V >: Null <: AnyRef]
  (implicit val can: ReactMap.Can[K, V])
extends ReactContainer[(K, V)] with ReactBuilder[(K, V), ReactMap[K, V]] {
  private var table: Array[ReactMap.Entry[K, V]] = null
  private var elems = 0
  private var entries = 0
  private[reactress] var keyContainer: EmitContainer[K] = null
  private[reactress] var valueContainer: EmitContainer[V] = null
  private[reactress] var insertsEmitter: Reactive.Emitter[(K, V)] = null
  private[reactress] var removesEmitter: Reactive.Emitter[(K, V)] = null
  private[reactress] var clearsEmitter: Reactive.Emitter[Unit] = null

  protected def init(k: K) {
    table = new Array(ReactMap.initSize)
    keyContainer = new EmitContainer[K](f => foreach((k, v) => f(k)), () => size)
    valueContainer = new EmitContainer[V](f => foreach((k, v) => f(v)), () => size)
    insertsEmitter = new Reactive.Emitter[(K, V)]
    removesEmitter = new Reactive.Emitter[(K, V)]
    clearsEmitter = new Reactive.Emitter[Unit]
  }

  init(null.asInstanceOf[K])

  def keys: ReactContainer[K] = keyContainer
  def values: ReactContainer[V] = valueContainer
  def inserts: Reactive[(K, V)] = insertsEmitter
  def removes: Reactive[(K, V)] = removesEmitter
  def clears: Reactive[Unit] = clearsEmitter

  def builder: ReactBuilder[(K, V), ReactMap[K, V]] = this

  def +=(kv: (K, V)) = {
    insert(kv._1, kv._2)
    true
  }

  def -=(kv: (K, V)) = {
    remove(kv._1)
  }

  def container = this

  val react = new ReactMap.Lifted[K, V](this)

  def foreach(f: (K, V) => Unit) {
    var i = 0
    while (i < table.length) {
      var entry = table(i)
      while (entry ne null) {
        f(entry.key, entry.value)
        entry = entry.next
      }
      i += 1
    }
  }

  def foreach(f: ((K, V)) => Unit) {
    foreach { (k, v) =>
      f((k, v))
    }
  }

  private def lookup(k: K): ReactMap.Entry[K, V] = {
    val pos = index(k)
    var entry = table(pos)

    while (entry != null && entry.key != k) {
      entry = entry.next
    }

    if (entry == null) null
    else entry
  }

  private[reactress] def ensure(k: K): ReactMap.Entry[K, V] = {
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

  private[reactress] def clean(entry: ReactMap.Entry[K, V]) {
    if (entry.value == null) {
      val pos = index(entry.key)
      table(pos) = table(pos).remove(entry)
    }
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
      entries += 1
    } else {
      previousValue = entry.value
      entry.value = v
    }

    if (previousValue == null) elems += 1
    else {
      keyContainer.removes += k
      valueContainer.removes += previousValue
      if (removesEmitter.hasSubscriptions) removesEmitter += (k, previousValue)
    }
    
    {
      keyContainer.inserts += k
      valueContainer.inserts += v
      if (insertsEmitter.hasSubscriptions) insertsEmitter += (k, v)
    }
    
    entry.propagate()

    previousValue
  }

  private def delete(k: K): V = {
    val pos = index(k)
    var entry = table(pos)

    while (entry != null && entry.key != k) {
      entry = entry.next
    }

    if (entry == null) null
    else {
      val previousValue = entry.value
      entry.value = null

      elems -= 1
      if (!entry.hasSubscriptions) {
        table(pos) = table(pos).remove(entry)
        entries -= 1
      }

      {
        keyContainer.removes += k
        valueContainer.removes += previousValue
        if (removesEmitter.hasSubscriptions) removesEmitter += (k, previousValue)
      }
      entry.propagate()

      previousValue
    }
  }

  private def checkResize() {
    if (entries * 1000 / ReactMap.loadFactor > table.length) {
      val otable = table
      val ncapacity = table.length * 2
      table = new Array(ncapacity)
      elems = 0
      entries = 0

      var opos = 0
      while (opos < otable.length) {
        var entry = otable(opos)
        while (entry != null) {
          val nextEntry = entry.next
          val pos = index(entry.key)
          entry.next = table(pos)
          table(pos) = entry
          entries += 1
          if (entry.value != null) elems += 1
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
          elems -= 1
          keyContainer.removes += entry.key
          valueContainer.removes += previousValue
          removesEmitter += (entry.key, previousValue)
        }

        entry = nextEntry
      }

      pos += 1
    }
    if (elems != 0) {
      throw new IllegalStateException("Size not zero after clear: " + elems)
    }

    clearsEmitter += ()
  }

  def size: Int = elems
  
  override def toString = {
    val elems = mutable.Buffer[(K, V)]()
    for (kv <- this) elems += kv
    s"ReactMap($size, ${elems.mkString(", ")})"
  }

}


object ReactMap {

  trait Entry[@spec(Int, Long, Double) K, V >: Null <: AnyRef]
  extends Signal.Default[V] {
    def outer: ReactMap[K, V]
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
    def newEntry(key: K, outer: ReactMap[K, V]): ReactMap.Entry[K, V]
  }

  implicit def canAnyRef[K, V >: Null <: AnyRef] = new Can[K, V] {
    def newEntry(k: K, o: ReactMap[K, V]) = new ReactMap.Entry[K, V] {
      def outer = o
      def key = k
      var value: V = _
      var next: Entry[K, V] = null
    }
  }

  implicit def canInt[V >: Null <: AnyRef] = new Can[Int, V] {
    def newEntry(k: Int, o: ReactMap[Int, V]) = new ReactMap.Entry[Int, V] {
      def outer = o
      def key = k
      var value: V = _
      var next: Entry[Int, V] = null
    }
  }

  implicit def canLong[V >: Null <: AnyRef] = new Can[Long, V] {
    def newEntry(k: Long, o: ReactMap[Long, V]) = new ReactMap.Entry[Long, V] {
      def outer = o
      def key = k
      var value: V = _
      var next: Entry[Long, V] = null
    }
  }

  implicit def canDouble[V >: Null <: AnyRef] = new Can[Double, V] {
    def newEntry(k: Double, o: ReactMap[Double, V]) = new ReactMap.Entry[Double, V] {
      def outer = o
      def key = k
      var value: V = _
      var next: Entry[Double, V] = null
    }
  }

  def apply[@spec(Int, Long, Double) K, V >: Null <: AnyRef](implicit can: Can[K, V]) = new ReactMap[K, V]()(can)

  class Lifted[@spec(Int, Long, Double) K, V >: Null <: AnyRef](val container: ReactMap[K, V])
  extends ReactContainer.Lifted[(K, V)] {
    def apply(k: K): Signal[V] = {
      container.ensure(k).signal(container.applyOrNil(k))
    }
  }

  val initSize = 16

  val loadFactor = 750

  implicit def factory[@spec(Int, Long, Double) K, V >: Null <: AnyRef] = new ReactBuilder.Factory[(K, V), ReactMap[K, V]] {
    def apply() = ReactMap[K, V]
  }

}





