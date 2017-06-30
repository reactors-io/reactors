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

  private def READ_WIDE(enode: ENode): Array[AnyRef] = enode.wide

  private def CAS_WIDE(enode: ENode, ov: Array[AnyRef], nv: Array[AnyRef]): Boolean = {
    unsafe.compareAndSwapObject(enode, ENodeWideOffset, ov, nv)
  }

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
    val n = new SNode(0, key, value)
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
      val snode = new SNode(hash, key, value)
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
          completeExpansion(parent, parentpos, enode, level)
          slowInsert(key, value, hash, level, enode.wide, parent)
        } else slowInsert(key, value, hash, level, node, parent)
      } else {
        // Replace the single node with a narrow node.
        val snode = old.asInstanceOf[SNode[K, V]]
        val nnode = newNarrowOrWideNode(snode, hash, key, value, level + 4)
        if (CAS(node, pos, snode, nnode)) Success
        else slowInsert(key, value, hash, level, node, parent)
      }
    } else {
      // There is another transaction in progress, help complete it.
      ???
    }
  }

  private def isFrozenSNode(n: AnyRef): Boolean = {
    n.isInstanceOf[FNode] && n.asInstanceOf[FNode].frozen.isInstanceOf[SNode[_, _]]
  }

  private def freeze(narrow: Array[AnyRef]): Unit = {
    var i = 0
    while (i < narrow.length) {
      val node = READ(narrow, i)
      if ((node eq null) || (node eq VNode)) {
        // Freeze null or vacant.
        // If it fails, then either someone helped or another txn is in progress.
        // If another txn is in progress, then reinspect the current slot.
        if (!CAS(narrow, i, node, FVNode)) i -= 1
      } else if (node.isInstanceOf[SNode[_, _]]) {
        // Freeze single node.
        // If it fails, then either someone helped or another txn is in progress.
        // If another txn is in progres, then reinspect the current slot.
        val fnode = new FNode(node)
        if (!CAS(narrow, i, node, fnode)) i -= 1
      } else if (node.isInstanceOf[Array[AnyRef]]) {
        sys.error("Unexpected case -- a narrow node should never have collisions.")
      } else if ((node eq FVNode) || isFrozenSNode(node)) {
        // We can skip, somebody else previously helped with freezing this node.
      } else if (node.isInstanceOf[FNode]) {
        // We still need to freeze the subtree recursively.
        val subnode = node.asInstanceOf[FNode].frozen.asInstanceOf[Array[AnyRef]]
        freeze(subnode.asInstanceOf[Array[AnyRef]])
      } else {
        // If some other txn is in progress, help complete it, then repeat.
        ???
      }
      i += 1
    }
  }

  private def sequentialInsert(
    sn: SNode[K, V], wide: Array[AnyRef], level: Int
  ): Unit = {
    val mask = wide.length - 1
    val pos = (sn.hash >>> level) & mask
    if (wide(pos) == null) wide(pos) = sn
    else sequentialInsert(sn, wide, level, pos)
  }

  @tailrec
  private def sequentialInsert(
    sn: SNode[K, V], wide: Array[AnyRef], level: Int, pos: Int
  ): Unit = {
    val old = wide(pos)
    if (old.isInstanceOf[SNode[_, _]]) {
      val oldsn = old.asInstanceOf[SNode[K, V]]
      val an = newNarrowOrWideNode(oldsn, sn, level + 4)
      wide(pos) = an
    } else if (old.isInstanceOf[Array[AnyRef]]) {
      val oldan = old.asInstanceOf[Array[AnyRef]]
      val npos = (sn.hash >>> (level + 4)) & (oldan.length - 1)
      if (oldan(npos) == null) oldan(npos) = sn
      else {
        if (oldan.length == 4) {
          val an = new Array[AnyRef](16)
          sequentialTransfer(oldan, an, level + 4)
          wide(pos) = an
          sequentialInsert(sn, wide, level, pos)
        } else sequentialInsert(sn, oldan, level + 4, npos)
      }
    }
  }

  private def sequentialTransfer(
    narrow: Array[AnyRef], wide: Array[AnyRef], level: Int
  ): Unit = {
    val mask = wide.length - 1
    var i = 0
    while (i < narrow.length) {
      val node = narrow(i)
      if (node eq FVNode) {
        // We can skip, the slot was empty.
      } else if (isFrozenSNode(node)) {
        // We can copy it over to the wide node.
        val snode = node.asInstanceOf[FNode].frozen.asInstanceOf[SNode[K, V]]
        val pos = (snode.hash >>> level) & mask
        if (wide(pos) == null) wide(pos) = snode
        else sequentialInsert(snode, wide, level, pos)
      } else if (node.isInstanceOf[FNode]) {
        sys.error("Unexpected case -- narrow array node should never have collisions.")
      } else {
        sys.error("Unexpected case -- narrow array node should have been frozen.")
      }
      i += 1
    }
  }

  private def newNarrowOrWideNode(
    sn1: SNode[K, V], sn2: SNode[K, V], level: Int
  ): Array[AnyRef] = {
    val pos1 = (sn1.hash >>> level) & (4 - 1)
    val pos2 = (sn2.hash >>> level) & (4 - 1)
    if (pos1 != pos2) {
      val an = new Array[AnyRef](4)
      val pos1 = (sn1.hash >>> level) & (an.length - 1)
      an(pos1) = sn1
      val pos2 = (sn2.hash >>> level) & (an.length - 1)
      an(pos2) = sn2
      an
    } else {
      val an = new Array[AnyRef](16)
      sequentialInsert(sn1, an, level)
      sequentialInsert(sn2, an, level)
      an
    }
  }

  private def newNarrowOrWideNode(
    sn1: SNode[K, V], h2: Int, k2: K, v2: V, level: Int
  ): Array[AnyRef] = {
    newNarrowOrWideNode(sn1, new SNode(h2, k2, v2), level)
  }

  private def newExpansionNode(narrow: Array[AnyRef]): ENode = {
    val fn = new ENode(narrow)
    fn
  }

  private def completeExpansion(
    parent: Array[AnyRef], parentpos: Int, enode: ENode, level: Int
  ): Unit = {
    // First, freeze the subtree beneath the narrow node.
    val narrow = enode.narrow
    freeze(narrow)

    // Second, populate the target array, and CAS it into the parent.
    var wide = new Array[AnyRef](16)
    sequentialTransfer(narrow, wide, level)
    // If this CAS fails, then somebody else already committed the wide array.
    if (!CAS_WIDE(enode, null, wide)) {
      wide = READ_WIDE(enode)
    }
    // We need to write the agreed value back into the parent.
    // If we failed, it means that somebody else succeeded.
    CAS(parent, parentpos, enode, wide)
  }
}


object CacheTrie {
  private val ArrayBase = Platform.unsafe.arrayBaseOffset(classOf[Array[AnyRef]])
  private val ArrayShift = {
    val scale = Platform.unsafe.arrayIndexScale(classOf[Array[AnyRef]])
    require((scale & (scale - 1)) == 0)
    31 - Integer.numberOfLeadingZeros(scale)
  }
  private val ENodeWideOffset = {
    val field = classOf[ENode].getField("wide")
    Platform.unsafe.objectFieldOffset(field)
  }

  /* result types */

  val Success = new AnyRef

  val Restart = new AnyRef

  /* node types */

  val VNode = new AnyRef

  class SNode[K <: AnyRef, V](
    val hash: Int,
    val key: K,
    val value: V
  )

  class ENode(
    val narrow: Array[AnyRef]
  ) {
    @volatile var wide: Array[AnyRef] = null
  }

  val FVNode = new AnyRef

  class FNode(
    val frozen: AnyRef
  )

  class XNode(
  )
}
