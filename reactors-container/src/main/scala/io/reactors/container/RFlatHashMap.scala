package io.reactors
package container



import scala.reflect.ClassTag



class RFlatHashMap[@spec(Int, Long, Double) K, @spec(Int, Long, Double) V](
  implicit val emptyKey: Arrayable[K],
  implicit val emptyVal: Arrayable[V]
) extends RContainer[K] {
  private var keytable: Array[K] = null
  private var valtable: Array[V] = null
  private var sz = 0
  private[reactors] var insertsEmitter: Events.Emitter[K] = null
  private[reactors] var removesEmitter: Events.Emitter[K] = null
  private[reactors] var pairInsertsEmitter: Events.Emitter[K] = null
  private[reactors] var pairRemovesEmitter: Events.Emitter[K] = null
  private[reactors] var subscription: Subscription = _
  private[reactors] var rawMap: RMap[K, V] = _

  protected def init(ek: Arrayable[K], ev: Arrayable[V]) {
    keytable = emptyKey.newArray(RFlatHashMap.initSize)
    valtable = emptyVal.newArray(RFlatHashMap.initSize)
    insertsEmitter = new Events.Emitter[K]
    removesEmitter = new Events.Emitter[K]
    pairInsertsEmitter = new Events.Emitter[K]
    pairRemovesEmitter = new Events.Emitter[K]
    subscription = Subscription.empty
    rawMap = new RFlatHashMap.AsMap[K, V](this)
  }

  init(emptyKey, emptyVal)

  def unsubscribe() = subscription.unsubscribe()

  def nil: V = emptyVal.nil

  def inserts: Events[K] = insertsEmitter

  def removes: Events[K] = removesEmitter

  /** Returns an `RMap` view of this hash map. May result in boxing if the map contains
   *  primitive values.
   */
  def asMap: RMap[K, V] = rawMap

  def foreachPair(f: (K, V) => Unit) {
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

  def foreachTuple(f: ((K, V)) => Unit) {
    foreachPair { (k, v) =>
      f((k, v))
    }
  }

  def foreach(f: K => Unit) {
    var i = 0
    while (i < keytable.length) {
      val k = keytable(i)
      if (k != emptyKey.nil) {
        f(k)
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

  private[reactors] def insert(k: K, v: V, notify: Boolean = true): V = {
    checkResize(this)

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
    
    val keyAdded = curr == nil

    if (keyAdded) sz += 1
    else notifyRemove(k, previousValue, notify)
    notifyInsert(k, v, notify)

    previousValue
  }

  private[reactors] def notifyInsert(k: K, v: V, notify: Boolean) {
    if (notify && insertsEmitter.hasSubscriptions)
      insertsEmitter.react(k, null)
    if (notify && pairInsertsEmitter.hasSubscriptions)
      pairInsertsEmitter.react(k, v.asInstanceOf[AnyRef])
  }

  private[reactors] def notifyRemove(k: K, v: V, notify: Boolean) {
    if (notify && removesEmitter.hasSubscriptions)
      removesEmitter.react(k, null)
      if (notify && pairRemovesEmitter.hasSubscriptions)
      pairRemovesEmitter.react(k, v.asInstanceOf[AnyRef])
  }

  private[reactors] def delete(k: K, expectedValue: V = emptyVal.nil): V = {
    var pos = index(k)
    val nil = emptyKey.nil
    var curr = keytable(pos)

    while (curr != nil && curr != k) {
      pos = (pos + 1) % keytable.length
      curr = keytable(pos)
    }

    if (curr == nil) emptyVal.nil
    else if (expectedValue == emptyVal.nil || expectedValue == curr) {
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

      keytable(h0) = emptyKey.nil
      valtable(h0) = emptyVal.nil
      sz -= 1
      notifyRemove(k, previousValue, true)

      previousValue
    } else emptyVal.nil
  }

  private[reactors] def checkResize(self: RFlatHashMap[K, V]) {
    if (sz * 1000 / RFlatHashMap.loadFactor > keytable.length) {
      val okeytable = keytable
      val ovaltable = valtable
      val ncapacity = keytable.length * 2
      keytable = emptyKey.newArray(ncapacity)
      valtable = emptyVal.newArray(ncapacity)
      sz = 0

      var pos = 0
      val nil = emptyKey.nil
      while (pos < okeytable.length) {
        val curr = okeytable(pos)
        if (curr != nil) {
          val dummy = insert(curr, ovaltable(pos), false)
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

  def apply(key: K): V = lookup(key) match {
    case `emptyVal`.nil => throw new NoSuchElementException("key: " + key)
    case v => v
  }

  def applyOrElse(key: K, otherValue: V): V = lookup(key) match {
    case `emptyVal`.nil => otherValue
    case v => v
  }

  def applyOrNil(key: K): V = lookup(key)

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

  def clear()(implicit sk: Spec[K], sv: Spec[V]) {
    var pos = 0
    val nil = emptyKey.nil
    while (pos < keytable.length) {
      if (keytable(pos) != nil) {
        val k = keytable(pos)
        val v = valtable(pos)

        keytable(pos) = emptyKey.nil
        valtable(pos) = emptyVal.nil
        sz -= 1
        notifyRemove(k, v, true)
      }

      pos += 1
    }
  }

  def size: Int = sz
}


object RFlatHashMap {
  val initSize = 16
  val loadFactor = 400

  implicit def factory[
    @spec(Int, Long, Double) K: Arrayable,
    @spec(Int, Long, Double) V: Arrayable
  ] = {
    new RMap.Factory[K, V, RFlatHashMap[K, V]] {
      def apply(
        inserts: Events[K], removes: Events[K]
      ): RFlatHashMap[K, V] = {
        val hm = new RFlatHashMap[K, V]
        hm.subscription = new Subscription.Composite(
          inserts.onReaction(new FactoryInsertObserver(hm)),
          removes.onReaction(new FactoryRemoveObserver(hm))
        )
        hm
      }
      def apply(f: RFlatHashMap[K, V] => Subscription): RFlatHashMap[K, V] = {
        val hm = new RFlatHashMap[K, V]
        hm.subscription = f(hm)
        hm
      }
    }
  }

  class FactoryInsertObserver[@spec(Int, Long, Double) K, V](
    hm: RFlatHashMap[K, V]
  ) extends Observer[K] {
    def react(x: K, v: Any) = hm.insert(x, v.asInstanceOf[V])
    def except(t: Throwable) = {}
    def unreact() = {}
  }

  class FactoryRemoveObserver[@spec(Int, Long, Double) K, V](
    hm: RFlatHashMap[K, V]
  ) extends Observer[K] {
    def react(x: K, v: Any) = hm.delete(x, v.asInstanceOf[V])
    def except(t: Throwable) = {}
    def unreact() = {}
  }

  class AsMap[@spec(Int, Long, Double) K, @spec(Int, Long, Double) V](
    val self: RFlatHashMap[K, V]
  ) extends RMap[K, V] {
    def apply(k: K) = self.apply(k)
    def inserts = self.pairInsertsEmitter
    def removes = self.pairRemovesEmitter
    def foreach(f: K => Unit) = self.foreach(f)
    def size = self.size
    def unsubscribe() = {}
  }
}
