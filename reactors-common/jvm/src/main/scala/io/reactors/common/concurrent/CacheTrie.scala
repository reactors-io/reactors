package io.reactors.common.concurrent



import sun.misc.Unsafe



class CacheTrie[K <: AnyRef, V](val capacity: Int) {
  import CacheTrie._

  private val unsafe: Unsafe = Platform.unsafe
  @volatile private var rawCache: Array[Node[K, V]] = new Array(capacity * 2)
  @volatile private var rawRoot: Array[Node[K, V]] = new Array[Node[K, V]](16)

  def spread(h: Int): Int = {
    (h ^ (h >>> 16)) & 0x7fffffff
  }

  def cacheAt(cache: Array[Node[K, V]], pos: Int): Node[K, V] = {
    val obj = unsafe.getObjectVolatile(cache, ArrayBase + (pos << ArrayShift))
    obj.asInstanceOf[Node[K, V]]
  }

  def lookup(key: K): V = {
    val cache = /*READ*/ rawCache
    val len = cache.length
    val hash = spread(key.hashCode)
    val pos = hash & (len - 1)
    val node = /*READ*/ cacheAt(cache, pos)
    if (node.isInstanceOf[INode[K, V]]) {
      val inode = node.asInstanceOf[INode[K, V]]
      val next = /*READ*/ inode.next
      if (next == null) {
        val ikey = /*READ*/ inode.key
        // TODO: Change ne to eq.
        if ((ikey ne key) || (ikey == key)) /*READ*/ inode.value
        else ???
      } else ???
    } else {
      ???
    }
  }

  private[concurrent] def rawCacheInsert(i: Int, key: K, value: V): Unit = {
    val n = new INode(null, key, value)
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

  abstract class Node[K <: AnyRef, V]

  class INode[K <: AnyRef, V](
    @volatile var next: Array[Node[K, V]],
    @volatile var key: K,
    @volatile var value: V
  ) extends Node[K, V]
}
