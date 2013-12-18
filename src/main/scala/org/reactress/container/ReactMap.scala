package org.reactress
package container



import scala.reflect.ClassTag



class ReactMap[@spec(Int, Long, Double) K, V >: Null <: AnyRef]
extends ReactContainer[(K, V)] with ReactBuilder[(K, V), ReactMap[K, V]] {
  private var table: Array[ReactMap.Entry[K, V]] = null
  private var sz = 0
  private[reactress] var insertsEmitter: Reactive.Emitter[(K, V)] = null
  private[reactress] var removesEmitter: Reactive.Emitter[(K, V)] = null
  private[reactress] var clearsEmitter: Reactive.Emitter[Unit] = null

  protected def init(k: K) {
    table = new Array(ReactMap.initSize)
    insertsEmitter = new Reactive.Emitter[(K, V)]
    removesEmitter = new Reactive.Emitter[(K, V)]
    clearsEmitter = new Reactive.Emitter[Unit]
  }

  init(null.asInstanceOf[K])

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

  val reactive = new ReactMap.Lifted[K, V](this)

  def foreach[U](f: (K, V) => U) {
    var i = 0
    while (i < table.length) {
      val entry = table(i)
      if (entry ne null) {
        f(entry.key, entry.value)
      }
      i += 1
    }
  }

  private def lookup(k: K): V = {
    var pos = index(k)
    var entry = table(pos)

    while (entry != null && entry.key != k) {
      pos = (pos + 1) % table.length
      entry = entry.next
    }

    if (entry == null) null
    else entry.value
  }

  private def insert(k: K, v: V): V = {
    assert(v != null)
    checkResize()

    var pos = index(k)
    var entry = table(pos)

    while (entry != null && entry.key != k) {
      pos = (pos + 1) % table.length
      entry = entry.next
    }

    var previousValue: V = null
    if (entry == null) {
      entry = new ReactMap.Entry[K, V](k)
      entry.value = v
      entry.next = table(pos)
      table(pos) = entry
    } else {
      previousValue = entry.value
      entry.value = v
    }

    if (previousValue == null) sz += 1
    else if (removesEmitter.hasSubscriptions) removesEmitter += (k, previousValue)
    if (insertsEmitter.hasSubscriptions) insertsEmitter += (k, v)
    entry.propagate()

    previousValue
  }

  private def delete(k: K): V = {
    var pos = index(k)
    var entry = table(pos)

    while (entry != null && entry.key != k) {
      pos = (pos + 1) % table.length
      entry = entry.next
    }

    if (entry == null) null
    else {
      val previousValue = entry.value
      entry.value = null

      sz -= 1
      if (!entry.hasSubscriptions) table(pos) = table(pos).remove(entry)

      if (removesEmitter.hasSubscriptions) removesEmitter += (k, previousValue)
      entry.propagate()

      previousValue
    }
  }

  private def checkResize() {
    if (sz * 1000 / ReactMap.loadFactor > table.length) {
      val otable = table
      val ncapacity = table.length * 2
      table = new Array(ncapacity)
      sz = 0

      var pos = 0
      while (pos < otable.length) {
        var entry = otable(pos)
        while (entry != null) {
          val nextEntry = entry.next
          entry.next = table(pos)
          table(pos) = entry
          entry = nextEntry
        }

        pos += 1
      }
    }
  }

  private def index(k: K): Int = {
    val hc = k.##
    math.abs(scala.util.hashing.byteswap32(hc)) % table.length
  }

  // TODO reactive apply

  def apply(key: K): V = lookup(key) match {
    case null => throw new NoSuchElementException("key: " + key)
    case v => v
  }

  def get(key: K): Option[V] = lookup(key) match {
    case null => None
    case v => Some(v)
  }

  def contains(key: K): Boolean = lookup(key) match {
    case null => false
    case v => true
  }

  def update(key: K, value: V): Unit = insert(key, value)

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

        entry.value = null
        if (!entry.hasSubscriptions) table(pos) = table(pos).remove(entry)
        entry.propagate()
        sz -= 1

        entry = nextEntry
      }

      pos += 1
    }

    clearsEmitter += ()
  }

  def size: Int = sz
  
}


object ReactMap {

  class Entry[@spec(Int, Long, Double) K, V >: Null <: AnyRef](val key: K)
  extends Signal.Default[V] {
    var value: V = _
    var next: Entry[K, V] = null
    def apply() = value
    def propagate() = reactAll(value)
    def remove(e: Entry[K, V]): Entry[K, V] = if (this eq e) next else {
      if (next ne null) next = next.remove(e)
      this
    }
  }

  def apply[@spec(Int, Long, Double) K, V >: Null <: AnyRef] = new ReactMap[K, V]

  class Lifted[@spec(Int, Long, Double) K, V >: Null <: AnyRef](val outer: ReactMap[K, V]) extends ReactContainer.Lifted[(K, V)] {
    def apply(k: K): Reactive[V] = ???
  }

  val initSize = 16

  val loadFactor = 750

  implicit def factory[@spec(Int, Long, Double) K, V >: Null <: AnyRef] = new ReactBuilder.Factory[(K, V), ReactMap[K, V]] {
    def apply() = ReactMap[K, V]
  }
}





