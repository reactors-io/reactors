package io.reactors.common.concurrent



import sun.misc.Unsafe



class CacheTrie[K, V](val capacity: Int) {
  import CacheTrie._

  @volatile
  private var rawCache: Array[Node[K, V]] = new Array(capacity * 2)
  private val unsafe: Unsafe = Platform.unsafe

  def spread(h: Int): Int = {
    (h ^ (h >>> 16)) & 0x7fffffff
  }

  def cacheAt(cache: Array[Node[K, V]], pos: Int): Node[K, V] = {
    unsafe.getObjectVolatile(cache, ArrayBase + (pos << ArrayShift))
    cache(pos)
  }

  def lookup(key: K): V = {
    val cache = /*READ*/ rawCache
    val len = cache.length
    val hash = spread(key.hashCode)
    val pos = hash & (len - 1)
    val node = cacheAt(cache, pos)
    if (node.isInstanceOf[SNode[K, V]]) {
      node.asInstanceOf[SNode[K, V]].value
    } else {
      ???
    }
  }

  def insert(i: Int, key: K, value: V): Unit = {
    val n = new SNode(key, value)
    rawCache(2 * i) = n
    rawCache(2 * i + 1) = n
  }
}


object CacheTrie {
  private val ArrayBase = Platform.unsafe.arrayBaseOffset(classOf[Array[Node[_, _]]])
  private val ArrayShift = {
    val scale = Platform.unsafe.arrayIndexScale(classOf[Array[Node[_, _]]])
    require((scale & (scale - 1)) == 0)
    31 - Integer.numberOfLeadingZeros(scale)
  }

  abstract class Node[K, V]

  class SNode[K, V](val key: K, val value: V) extends Node[K, V]
}
