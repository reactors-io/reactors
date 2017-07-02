package io.reactors.common.concurrent



import sun.misc.Unsafe
import scala.annotation.tailrec



class CacheTrie[K <: AnyRef, V](val capacity: Int) {
  import CacheTrie._

  private val unsafe: Unsafe = Platform.unsafe
  @volatile private var rawCache: Array[AnyRef] = new Array(capacity * 2)
  private val rawRoot: Array[AnyRef] = new Array[AnyRef](16)

  def this() = this(0)

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

  private[concurrent] def fastLookup(key: K): V = {
    val cache = READ_CACHE
    val len = cache.length
    val hash = spread(key.hashCode)
    val pos = hash & (len - 1)
    val node = READ(cache, pos)
    if (node.isInstanceOf[SNode[K, V]]) {
      val inode = node.asInstanceOf[SNode[K, V]]
      val ikey = inode.key
      val ihash = inode.hash
      // TODO: Change ne to eq.
      if ((ihash != hash) && ((ikey ne key) || (ikey == key))) inode.value
      else null.asInstanceOf[V]
    } else {
      ???
    }
  }

  private[concurrent] def unsafeCacheInsert(i: Int, key: K, value: V): Unit = {
    val n = new SNode(0, key, value)
    rawCache(2 * i) = n
    rawCache(2 * i + 1) = n
  }

  final def lookup(key: K): V = {
    slowLookup(key)
  }

  private[concurrent] def slowLookup(key: K): V = {
    val node = rawRoot
    val hash = spread(key.hashCode)
    slowLookup(key, hash, 0, node)
  }

  @tailrec
  private[concurrent] final def slowLookup(
    key: K, hash: Int, level: Int, node: Array[AnyRef]
  ): V = {
    val mask = node.length - 1
    val pos = (hash >>> level) & mask
    val old = READ(node, pos)
    if ((old eq null) || (old eq VNode)) {
      null.asInstanceOf[V]
    } else if (old.isInstanceOf[Array[AnyRef]]) {
      slowLookup(key, hash, level + 4, old.asInstanceOf[Array[AnyRef]])
    } else if (old.isInstanceOf[SNode[_, _]]) {
      val oldsn = old.asInstanceOf[SNode[K, V]]
      if ((oldsn.hash == hash) && ((oldsn.key eq key) || (oldsn.key == key))) {
        oldsn.value
      } else {
        null.asInstanceOf[V]
      }
    } else if (old.isInstanceOf[LNode[_, _]]) {
      val oldln = old.asInstanceOf[LNode[K, V]]
      if (oldln.hash != hash) {
        null.asInstanceOf[V]
      } else {
        var tail = oldln
        while (tail != null) {
          if ((tail.key eq key) || (tail.key == key)) {
            return tail.value
          }
          tail = tail.next
        }
        null.asInstanceOf[V]
      }
    } else {
      ???
    }
  }

  final def insert(key: K, value: V): Unit = {
    val node = rawRoot
    val hash = spread(key.hashCode)
    var result = Restart
    do {
      result = slowInsert(key, value, hash, 0, node, null)
    } while (result == Restart)
  }

  @tailrec
  private[concurrent] final def slowInsert(
    key: K, value: V, hash: Int, level: Int,
    current: Array[AnyRef], parent: Array[AnyRef]
  ): AnyRef = {
    val mask = current.length - 1
    val pos = (hash >>> level) & mask
    val old = READ(current, pos)
    if ((old eq null) || (old eq VNode)) {
      // Fast-path -- CAS the node into the empty position.
      val snode = new SNode(hash, key, value)
      if (CAS(current, pos, old, snode)) Success
      else slowInsert(key, value, hash, level, current, parent)
    } else if (old.isInstanceOf[Array[_]]) {
      // Repeat the search on the next level.
      slowInsert(key, value, hash, level + 4, old.asInstanceOf[Array[AnyRef]], current)
    } else if (old.isInstanceOf[SNode[_, _]]) {
      val oldsn = old.asInstanceOf[SNode[K, V]]
      if ((oldsn.hash == hash) && ((oldsn.key eq key) || (oldsn.key == key))) {
        val sn = new SNode(hash, key, value)
        if (CAS(current, pos, oldsn, sn)) Success
        else slowInsert(key, value, hash, level, current, parent)
      } else {
        if (current.length == 4) {
          // Expand the current node, seeking to avoid the collision.
          // Root size always 16, so parent is non-null.
          val parentmask = parent.length - 1
          val parentlevel = level - 4
          val parentpos = (hash >>> parentlevel) & parentmask
          val enode = new ENode(parent, parentpos, current, level)
          if (CAS(parent, parentpos, current, enode)) {
            completeExpansion(enode)
            slowInsert(key, value, hash, level, enode.wide, parent)
          } else slowInsert(key, value, hash, level, current, parent)
        } else {
          // Replace the single node with a narrow node.
          val nnode = newNarrowOrWideNode(oldsn, hash, key, value, level + 4)
          if (CAS(current, pos, oldsn, nnode)) Success
          else slowInsert(key, value, hash, level, current, parent)
        }
      }
    } else if (old.isInstanceOf[LNode[_, _]]) {
      val oldln = old.asInstanceOf[LNode[K, V]]
      val nn = newListNarrowOrWideNode(oldln, hash, key, value, level + 4)
      if (CAS(current, pos, oldln, nn)) Success
      else slowInsert(key, value, hash, level, current, parent)
    } else if (old.isInstanceOf[ENode]) {
      // There is another transaction in progress, help complete it, then restart.
      val enode = old.asInstanceOf[ENode]
      completeExpansion(enode)
      Restart
    } else if ((old eq FVNode) || old.isInstanceOf[FNode]) {
      // We landed into the middle of some other thread's transaction.
      // We need to restart from the top to find the transaction's descriptor,
      // and if we find it, then help and restart once more.
      Restart
    } else {
      sys.error("Unexpected case -- " + old)
    }
  }

  private def isFrozenSNode(n: AnyRef): Boolean = {
    n.isInstanceOf[FNode] && n.asInstanceOf[FNode].frozen.isInstanceOf[SNode[_, _]]
  }

  private def isFrozenLNode(n: AnyRef): Boolean = {
    n.isInstanceOf[FNode] && n.asInstanceOf[FNode].frozen.isInstanceOf[LNode[_, _]]
  }

  private def freeze(current: Array[AnyRef]): Unit = {
    var i = 0
    while (i < current.length) {
      val node = READ(current, i)
      if ((node eq null) || (node eq VNode)) {
        // Freeze null or vacant.
        // If it fails, then either someone helped or another txn is in progress.
        // If another txn is in progress, then reinspect the current slot.
        if (!CAS(current, i, node, FVNode)) i -= 1
      } else if (node.isInstanceOf[SNode[_, _]]) {
        // Freeze single node.
        // If it fails, then either someone helped or another txn is in progress.
        // If another txn is in progress, then we must reinspect the current slot.
        val fnode = new FNode(node)
        if (!CAS(current, i, node, fnode)) i -= 1
      } else if (node.isInstanceOf[LNode[_, _]]) {
        // Freeze list node.
        // If it fails, then either someone helped or another txn is in progress.
        // If another txn is in progress, then we must reinspect the current slot.
        val fnode = new FNode(node)
        if (!CAS(current, i, node, fnode)) i -= 1
      } else if (node.isInstanceOf[Array[AnyRef]]) {
        // Freeze the array node.
        // If it fails, then either someone helped or another txn is in progress.
        // If another txn is in progress, then reinspect the current slot.
        val fnode = new FNode(node)
        if (!CAS(current, i, node, fnode)) i -= 1
      } else if ((node eq FVNode) || isFrozenSNode(node) || isFrozenLNode(node)) {
        // We can skip, somebody else previously helped with freezing this node.
      } else if (node.isInstanceOf[FNode]) {
        // We still need to freeze the subtree recursively.
        val subnode = node.asInstanceOf[FNode].frozen.asInstanceOf[Array[AnyRef]]
        freeze(subnode.asInstanceOf[Array[AnyRef]])
      } else if (node eq FVNode) {
        // We can continue, another thread already froze this slot.
      } else if (node.isInstanceOf[ENode]) {
        // If some other txn is in progress, help complete it,
        // then restart from current position.
        val enode = node.asInstanceOf[ENode]
        completeExpansion(enode)
        i -= 1
      } else {
        sys.error("Unexpected case -- " + node)
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
    } else if (old.isInstanceOf[LNode[_, _]]) {
      val oldln = old.asInstanceOf[LNode[K, V]]
      val nn = newListNarrowOrWideNode(oldln, sn.hash, sn.key, sn.value, level + 4)
      wide(pos) = nn
    } else {
      sys.error("Unexpected case: " + old)
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
      } else if (isFrozenLNode(node)) {
        var tail = node.asInstanceOf[FNode].frozen.asInstanceOf[LNode[K, V]]
        while (tail != null) {
          val sn = new SNode(tail.hash, tail.key, tail.value)
          val pos = (sn.hash >>> level) & mask
          sequentialInsert(sn, wide, level, pos)
          tail = tail.next
        }
      } else if (node.isInstanceOf[FNode]) {
        // TODO: Revisit this.
        sys.error("Unexpected case -- narrow array node should never have collisions.")
      } else {
        sys.error("Unexpected case -- narrow array node should have been frozen.")
      }
      i += 1
    }
  }

  private def newNarrowOrWideNode(
    sn1: SNode[K, V], sn2: SNode[K, V], level: Int
  ): AnyRef = {
    if (sn1.hash == sn2.hash) {
      val ln1 = new LNode(sn1)
      val ln2 = new LNode(sn2, ln1)
      ln2
    } else {
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
  }

  private def newNarrowOrWideNode(
    sn1: SNode[K, V], h2: Int, k2: K, v2: V, level: Int
  ): AnyRef = {
    newNarrowOrWideNode(sn1, new SNode(h2, k2, v2), level)
  }

  private def newListNarrowOrWideNode(
    ln: CacheTrie.LNode[K, V], hash: Int, k: K, v: V, level: Int
  ): AnyRef = {
    if (ln.hash == hash) {
      new LNode(hash, k, v, ln)
    } else {
      val an = new Array[AnyRef](16)
      val pos1 = (ln.hash >>> level) & (an.length - 1)
      an(pos1) = ln
      val sn = new SNode(hash, k, v)
      sequentialInsert(sn, an, level)
      an
    }
  }

  private def completeExpansion(enode: ENode): Unit = {
    val parent = enode.parent
    val parentpos = enode.parentpos
    val level = enode.level

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

  private[concurrent] def debugTree: String = {
    val res = new StringBuilder
    def traverse(indent: String, node: Array[AnyRef]): Unit = {
      var i = 0
      while (i < node.length) {
        val old = READ(node, i)
        if (old == null) {
          res.append(s"${indent}<empty>")
          res.append("\n")
        } else if (old eq VNode) {
          res.append(s"${indent}<empty-cleared>")
          res.append("\n")
        } else if (old.isInstanceOf[SNode[_, _]]) {
          val sn = old.asInstanceOf[SNode[K, V]]
          res.append(s"${indent}")
          res.append(s"SN[${Integer.toHexString(sn.hash)}:${sn.key}:${sn.value}]")
          res.append("\n")
        } else if (old.isInstanceOf[LNode[_, _]]) {
          var ln = old.asInstanceOf[LNode[K, V]]
          while (ln != null) {
            res.append(s"${indent}")
            res.append(s"LN[${Integer.toHexString(ln.hash)}:${ln.key}:${ln.value}]")
            res.append("->")
            ln = ln.next
          }
          res.append("\n")
        } else if (old.isInstanceOf[Array[AnyRef]]) {
          val an = old.asInstanceOf[Array[AnyRef]]
          res.append(s"${indent}${if (an.length == 4) "narrow" else "wide"}")
          res.append("\n")
          traverse(indent + "  ", an)
        } else {
          res.append("unsupported case: " + old)
        }
        i += 1
      }
    }
    traverse("", rawRoot)
    res.toString
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
    val field = classOf[ENode].getDeclaredField("wide")
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

  class LNode[K <: AnyRef, V](
    val hash: Int,
    val key: K,
    val value: V,
    val next: LNode[K, V]
  ) {
    def this(sn: SNode[K, V], next: LNode[K, V]) = this(sn.hash, sn.key, sn.value, next)
    def this(sn: SNode[K, V]) = this(sn, null)
  }

  class ENode(
    val parent: Array[AnyRef],
    val parentpos: Int,
    val narrow: Array[AnyRef],
    val level: Int
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
