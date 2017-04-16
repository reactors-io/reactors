package io.reactors
package common



import java.util.HashMap
import scala.util.hashing.Hashing



/** Bloom-filtered hash map that has fast checks when the key is not in the map.
 *
 *  Equality and hashing are reference-based.
 *
 *  The fast checks use identity hash. The map cannot contain `null` keys or values.
 *  Values are specialized for integers and longs.
 */
class BloomMap[K >: Null <: AnyRef: Arrayable, @specialized(Int, Long) V: Arrayable] {
  private var keytable = implicitly[Arrayable[K]].newRawArray(8)
  private var valtable = implicitly[Arrayable[V]].newArray(8)
  private var rawSize = 0
  private var bloom = new Array[Byte](4)
  private val rawNil = implicitly[Arrayable[V]].nil

  def contains(key: K): Boolean = {
    val hash = System.identityHashCode(key)
    val idx = (hash >>> 3) % bloom.length
    val pos = 1 << (hash & 0x7)
    val down = (bloom(idx) & pos) == 0
    if (down) false
    else lookup(key) != rawNil
  }

  private def lookup(key: K): V = {
    var pos = System.identityHashCode(key) % keytable.length
    var curr = keytable(pos)

    while (curr != null && (curr ne key)) {
      pos = (pos + 1) % keytable.length
      curr = keytable(pos)
    }

    valtable(pos)
  }

  def nil: V = rawNil

  def get(key: K): V = {
    val hash = System.identityHashCode(key)
    val idx = (hash >>> 3) % bloom.length
    val pos = 1 << (hash & 0x7)
    val down = (bloom(idx) & pos) == 0
    if (down) rawNil
    else lookup(key)
  }

  private def insert(key: K, value: V): V = {
    assert(key != null)
    checkResize(rawNil)

    var pos = System.identityHashCode(key) % keytable.length
    var curr = keytable(pos)
    while (curr != null && (curr ne key)) {
      pos = (pos + 1) % keytable.length
      curr = keytable(pos)
    }

    val previousValue = valtable(pos)
    keytable(pos) = key
    valtable(pos) = value
    
    val keyAdded = curr == null
    if (keyAdded) rawSize += 1
    previousValue
  }

  private def checkResize(nil: V): Unit = {
    if (rawSize * 1000 / BloomMap.loadFactor > keytable.length) {
      resize(nil)
    }
  }

  private def resize(nil: V): Unit = {
    val okeytable = keytable
    val ovaltable = valtable
    val ncapacity = keytable.length * 2
    keytable = implicitly[Arrayable[K]].newRawArray(ncapacity)
    valtable = implicitly[Arrayable[V]].newArray(ncapacity)
    rawSize = 0

    var pos = 0
    while (pos < okeytable.length) {
      val curr = okeytable(pos)
      if (curr != null) {
        val dummy = insert(curr, ovaltable(pos))
      }
      pos += 1
    }
  }

  private def resizeBloomFilter() {
    bloom = new Array(keytable.length / 2)
    var i = 0
    while (i < keytable.length) {
      val key = keytable(i)
      if (key != null) {
        val hash = System.identityHashCode(key)
        val idx = (hash >>> 3) % bloom.length
        val pos = 1 << (hash & 0x7)
        bloom(idx) = (bloom(idx) | pos).toByte
      }
      i += 1
    }
  }

  def put(key: K, value: V): Unit = {
    insert(key, value)
    if (bloom.length > keytable.length / 4) {
      val hash = System.identityHashCode(key)
      val idx = (hash >>> 3) % bloom.length
      val pos = 1 << (hash & 0x7)
      bloom(idx) = (bloom(idx) | pos).toByte
    } else resizeBloomFilter()
  }

  def size: Int = rawSize

  def isEmpty: Boolean = size == 0

  def nonEmpty: Boolean = !isEmpty

  private def before(i: Int, j: Int): Boolean = {
    val d = keytable.length >> 1
    if (i <= j) j - i < d
    else i - j > d
  }

  private def delete(key: K): V = {
    assert(key != null)
    var pos = System.identityHashCode(key) % keytable.length
    var curr = keytable(pos)

    while (curr != null && (curr ne key)) {
      pos = (pos + 1) % keytable.length
      curr = keytable(pos)
    }

    if (curr == null) rawNil
    else {
      val previousValue = valtable(pos)

      var h0 = pos
      var h1 = (h0 + 1) % keytable.length
      while (keytable(h1) != null) {
        val h2 = System.identityHashCode(keytable(h1)) % keytable.length
        if (h2 != h1 && before(h2, h0)) {
          keytable(h0) = keytable(h1)
          valtable(h0) = valtable(h1)
          h0 = h1
        }
        h1 = (h1 + 1) % keytable.length
      }

      keytable(h0) = null
      valtable(h0) = rawNil
      rawSize -= 1

      previousValue
    }
  }

  def remove(key: K): V = delete(key)

  def clear()(implicit spec: BloomMap.Spec[V]): Unit = {
    var i = 0
    while (i < keytable.length) {
      keytable(i) = null
      valtable(i) = rawNil
      i += 1
    }
    i = 0
    while (i < bloom.length) {
      bloom(i) = 0
      i += 1
    }
  }
}


object BloomMap {
  val loadFactor = 400

  class Spec[@specialized(Int, Long) T]

  implicit val intSpec = new Spec[Int]

  implicit val longSpec = new Spec[Long]

  private val anySpecValue = new Spec[Any]

  implicit def anySpec[T] = anySpecValue.asInstanceOf[Spec[T]]
}
