package io.reactors.common.concurrent



import sun.misc.Unsafe
import scala.annotation.tailrec



class CacheTrie[K <: AnyRef, V](val capacity: Int) {
  import CacheTrie._

  private val unsafe: Unsafe = Platform.unsafe
  @volatile private var rawCache: Array[AnyRef] = new Array(capacity * 2)
  private val rawRoot: Array[AnyRef] = new Array[AnyRef](16)

  private def spread(h: Int): Int = {
    (h ^ (h >>> 16)) & 0x7fffffff
  }

  private def READ(array: Array[AnyRef], pos: Int): AnyRef = {
    unsafe.getObjectVolatile(array, ArrayBase + (pos << ArrayShift))
  }

  private def CAS(array: Array[AnyRef], pos: Int, ov: AnyRef, nv: AnyRef): Boolean = {
    unsafe.compareAndSwapObject(array, ArrayBase + (pos << ArrayShift), ov, nv)
  }

  private def READ_CACHE: Array[AnyRef] = rawCache

  def fastLookup(key: K): V = {
    val cache = READ_CACHE
    val len = cache.length
    val hash = spread(key.hashCode)
    val pos = hash & (len - 1)
    val node = READ(cache, pos)
    if (node.isInstanceOf[SNode[K, V]]) {
      val inode = node.asInstanceOf[SNode[K, V]]
      val ikey = inode.key
      // TODO: Change ne to eq.
      if ((ikey ne key) || (ikey == key)) inode.value
      else ???
    } else {
      ???
    }
  }

  private[concurrent] def unsafeCacheInsert(i: Int, key: K, value: V): Unit = {
    val n = new SNode(key, value)
    rawCache(2 * i) = n
    rawCache(2 * i + 1) = n
  }

  final def insert(key: K, value: V): Unit = {
    val node = rawRoot
    val hash = spread(key.hashCode)
    slowInsert(key, value, hash, 0, node, null)
  }

  @tailrec
  private[concurrent] final def slowInsert(
    key: K, value: V, hash: Int, level: Int, node: Array[AnyRef], parent: Array[AnyRef]
  ): AnyRef = {
    val mask = node.length - 1
    val pos = (hash >>> level) & mask
    val old = READ(node, pos)
    if ((old eq null) || (old eq VNode)) {
      // Fast-path -- CAS the node into the empty position.
      val snode = new SNode(key, value)
      if (CAS(node, pos, old, snode)) Success
      else slowInsert(key, value, hash, level, node, parent)
    } else if (old.isInstanceOf[Array[_]]) {
      // Repeat the search on the next level.
      slowInsert(key, value, hash, level + 4, old.asInstanceOf[Array[AnyRef]], node)
    } else if (old.isInstanceOf[SNode[_, _]]) {
      if (node.length == 4) {
        // Expand the current node, seeking to avoid the collision.
        val enode = newExpansionNode(node)
        // Root size always 16, so parent is non-null.
        val parentmask = parent.length - 1
        val parentlevel = level - 4
        val parentpos = (hash >>> parentlevel) & parentmask
        if (CAS(parent, parentpos, node, enode)) {
          completeExpansion(parent, parentpos, enode)
          slowInsert(key, value, hash, level, enode.wide, parent)
        } else slowInsert(key, value, hash, level, node, parent)
      } else {
        // Replace the single node with a narrow node.
        val snode = old.asInstanceOf[SNode[_, _]]
        val nnode = newNarrowOrWideNode(snode, key, value)
        if (CAS(node, pos, snode, nnode)) Success
        else slowInsert(key, value, hash, level, node, parent)
      }
    } else {
      ???
    }
  }

  private def completeExpansion(
    parent: Array[AnyRef], parentpos: Int, enode: ENode
  ): Unit = {
    ???
  }
}


object CacheTrie {
  private val ArrayBase = Platform.unsafe.arrayBaseOffset(classOf[Array[AnyRef]])
  private val ArrayShift = {
    val scale = Platform.unsafe.arrayIndexScale(classOf[Array[AnyRef]])
    require((scale & (scale - 1)) == 0)
    31 - Integer.numberOfLeadingZeros(scale)
  }

  /* result types */

  val Success = new AnyRef

  val Restart = new AnyRef

  /* node types */

  val VNode = new AnyRef

  class SNode[K <: AnyRef, V](
    val key: K,
    val value: V
  )

  def newNarrowOrWideNode[K, V](sn1: SNode[K, V], k2: K, v2: V): Array[AnyRef] = {
    ???
  }

  class ENode(
    val narrow: Array[AnyRef],
    val wide: Array[AnyRef]
  )

  def newExpansionNode(narrow: Array[AnyRef]): ENode = {
    val wide = new Array[AnyRef](16)
    val fn = new ENode(narrow, wide)
    fn
  }

  val TNode = new AnyRef

  class FNode(
    val frozen: AnyRef
  )

  class XNode(
  )
}
