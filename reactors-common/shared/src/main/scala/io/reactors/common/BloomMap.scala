package io.reactors
package common



import java.util.HashMap
import scala.util.hashing.Hashing



/** Bloom-filtered hash map that has fast checks when the key is not in the map.
 *
 *  The fast checks use reference checks. The map cannot contain `null` keys or values.
 */
class BloomMap[K >: Null <: AnyRef: Arrayable, V >: Null <: AnyRef: Arrayable] {
  private var keytable = implicitly[Arrayable[K]].newRawArray(8)
  private var valtable = implicitly[Arrayable[V]].newRawArray(8)
  private var rawSize = 0
  private var bloom = new Array[Byte](4)

  def contains(key: K): Boolean = {
    val hash = System.identityHashCode(key)
    val idx = (hash >>> 3) % bloom.length
    val pos = 1 << (hash & 0x7)
    val down = (bloom(idx) & pos) == 0
    if (down) false
    else lookup(key) != null
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

  def get(key: K): V = {
    val hash = System.identityHashCode(key)
    val idx = (hash >>> 3) % bloom.length
    val pos = 1 << (hash & 0x7)
    val down = (bloom(idx) & pos) == 0
    if (down) null
    else lookup(key)
  }

  private def insert(key: K, value: V): V = {
    assert(key != null)
    checkResize()

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

  private def checkResize() {
    if (rawSize * 1000 / BloomMap.loadFactor > keytable.length) {
      val okeytable = keytable
      val ovaltable = valtable
      val ncapacity = keytable.length * 2
      keytable = implicitly[Arrayable[K]].newArray(ncapacity)
      valtable = implicitly[Arrayable[V]].newArray(ncapacity)
      rawSize = 0

      var pos = 0
      while (pos < okeytable.length) {
        val curr = okeytable(pos)
        if (curr != null) insert(curr, ovaltable(pos))
        pos += 1
      }
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

  def size = rawSize

  private def before(i: Int, j: Int) = {
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

    if (curr == null) null
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
      valtable(h0) = null
      rawSize -= 1

      previousValue
    }
  }

  def remove(key: K): V = delete(key)

  def clear(): Unit = {
    var i = 0
    while (i < keytable.length) {
      keytable(i) = null
      valtable(i) = null
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
}
