package org.reactress
package container



import scala.reflect.ClassTag



class ReactMap[@spec(Int, Long) K, V](
  implicit val emptyKey: ReactMap.Empty[K],
  implicit val emptyVal: ReactMap.Empty[V]
) extends ReactContainer[(K, V), ReactMap[K, V]] with ReactBuilder[(K, V), ReactMap[K, V]] {
  private var keytable: Array[K] = null
  private var valtable: Array[V] = emptyVal.newEmptyArray(ReactMap.initSize)
  private var sz = 0
  private[reactress] var insertsEmitter: Reactive.Emitter[(K, V)] = null
  private[reactress] var removesEmitter: Reactive.Emitter[(K, V)] = null
  private[reactress] var clearsEmitter: Reactive.Emitter[Unit] = null

  protected def init(ek: ReactMap.Empty[K], ev: ReactMap.Empty[V]) {
    keytable = emptyKey.newEmptyArray(ReactMap.initSize)
    insertsEmitter = new Reactive.Emitter[(K, V)]
    removesEmitter = new Reactive.Emitter[(K, V)]
    clearsEmitter = new Reactive.Emitter[Unit]
  }

  init(emptyKey, emptyVal)

  def inserts: Reactive[(K, V)] = insertsEmitter
  def removes: Reactive[(K, V)] = removesEmitter
  def clears: Reactive[Unit] = clearsEmitter

  def builder: ReactBuilder[(K, V), ReactMap[K, V]] = this

  def +=(kv: (K, V)) = {
    update(kv._1, kv._2)
    this
  }

  def -=(kv: (K, V)) = {
    remove(kv._1)
    this
  }

  def result = this

  def foreach[U](f: (K, V) => U) {
    var i = 0
    while (i < keytable.length) {
      val k = keytable(i)
      if (k != emptyKey.nil) {
        val v = valtable(i)
        f(k, v)
      }
      i += 1
    }
  }

  private def lookup(k: K): V = {
    var pos = index(k)
    val nil = emptyKey.nil
    var curr = keytable(pos)

    while (curr != nil && curr != k) {
      pos = (pos + 1) % keytable.length
      curr = keytable(pos)
    }

    if (curr == nil) emptyVal.nil
    else valtable(pos)
  }

  private def insert(k: K, v: V, notify: Boolean = true): V = {
    checkResize()

    var pos = index(k)
    val nil = emptyKey.nil
    var curr = keytable(pos)
    assert(k != nil)

    while (curr != nil && curr != k) {
      pos = (pos + 1) % keytable.length
      curr = keytable(pos)
    }

    val previousValue = valtable(pos)
    keytable(pos) = k
    valtable(pos) = v
    val added = curr == nil
    if (added) {
      sz += 1
      if (notify && insertsEmitter.hasSubscriptions) insertsEmitter += (k, v)
    }

    previousValue
  }

  private def delete(k: K): V = {
    var pos = index(k)
    val nil = emptyKey.nil
    var curr = keytable(pos)

    while (curr != nil && curr != k) {
      pos = (pos + 1) % keytable.length
      curr = keytable(pos)
    }

    if (curr == nil) emptyVal.nil
    else {
      val previousValue = valtable(pos)

      var h0 = pos
      var h1 = (h0 + 1) % keytable.length
      while (keytable(h1) != nil) {
        val h2 = index(keytable(h1))
        if (h2 != h1 && before(h2, h0)) {
          keytable(h0) = keytable(h1)
          valtable(h0) = valtable(h1)
          h0 = h1
        }
        h1 = (h1 + 1) % keytable.length
      }

      val v = valtable(h0)
      keytable(h0) = emptyKey.nil
      valtable(h0) = emptyVal.nil
      sz -= 1
      if (removesEmitter.hasSubscriptions) removesEmitter += (k, v)

      previousValue
    }
  }

  private def checkResize() {
    if (sz * 1000 / ReactMap.loadFactor > keytable.length) {
      val okeytable = keytable
      val ovaltable = valtable
      val ncapacity = keytable.length * 2
      keytable = emptyKey.newEmptyArray(ncapacity)
      valtable = emptyVal.newEmptyArray(ncapacity)
      sz = 0

      var pos = 0
      val nil = emptyKey.nil
      while (pos < okeytable.length) {
        val curr = okeytable(pos)
        if (curr != nil) {
          insert(curr, ovaltable(pos), false)
        }

        pos += 1
      }
    }
  }

  private def before(i: Int, j: Int) = {
    val d = keytable.length >> 1
    if (i <= j) j - i < d
    else i - j > d
  }

  private def index(k: K): Int = {
    val hc = k.##
    math.abs(scala.util.hashing.byteswap32(hc)) % keytable.length
  }

  // TODO reactive apply

  def apply(key: K): V = lookup(key) match {
    case `emptyVal`.nil => throw new NoSuchElementException("key: " + key)
    case v => v
  }

  def get(key: K): Option[V] = lookup(key) match {
    case `emptyVal`.nil => None
    case v => Some(v)
  }

  def contains(key: K): Boolean = lookup(key) match {
    case `emptyVal`.nil => false
    case v => true
  }

  def update(key: K, value: V): Unit = insert(key, value)

  def remove(key: K): Boolean = delete(key) match {
    case `emptyVal`.nil => false
    case v => true
  }

  def clear() {
    var pos = 0
    val nil = emptyKey.nil
    while (pos < keytable.length) {
      if (keytable(pos) != nil) {
        val k = keytable(pos)
        val v = valtable(pos)

        keytable(pos) = emptyKey.nil
        valtable(pos) = emptyVal.nil
        sz -= 1
      }

      pos += 1
    }

    clearsEmitter += ()
  }

  def size: Int = sz
  
}


object ReactMap {

  def apply[@spec(Int, Long) K: Empty, V: Empty]() = new ReactMap[K, V]

  val initSize = 16

  val loadFactor = 450

  abstract class Empty[@spec(Int, Long) T] {
    val classTag: ClassTag[T]
    val nil: T
    def newEmptyArray(sz: Int): Array[T]
  }

  implicit def emptyRef[T >: Null <: AnyRef: ClassTag]: Empty[T] = new Empty[T] {
    val classTag = implicitly[ClassTag[T]]
    val nil = null
    def newEmptyArray(sz: Int) = new Array[T](sz)
  }

  implicit val emptyLong: Empty[Long] = new Empty[Long] {
    val classTag = implicitly[ClassTag[Long]]
    val nil = Long.MinValue
    def newEmptyArray(sz: Int) = Array.fill[Long](sz)(nil)
  }

  implicit val emptyInt: Empty[Int] = new Empty[Int] {
    val classTag = implicitly[ClassTag[Int]]
    val nil = Int.MinValue
    def newEmptyArray(sz: Int) = Array.fill[Int](sz)(nil)
  }

  implicit def factory[@spec(Int, Long) K: Empty, @spec(Int, Long) V: Empty] = new ReactBuilder.Factory[ReactMap[_, _], (K, V), ReactMap[K, V]] {
    def create() = new ReactMap[K, V]
  }
}





