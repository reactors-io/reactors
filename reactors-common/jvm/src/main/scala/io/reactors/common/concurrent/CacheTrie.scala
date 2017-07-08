package io.reactors.common.concurrent



import sun.misc.Unsafe
import scala.annotation.tailrec



class CacheTrie[K <: AnyRef, V] {
  import CacheTrie._

  private val unsafe: Unsafe = Platform.unsafe
  @volatile private var rawCache: Array[AnyRef] = null
  private val rawRoot: Array[AnyRef] = new Array[AnyRef](16)

  private def READ(array: Array[AnyRef], pos: Int): AnyRef = {
    unsafe.getObjectVolatile(array, ArrayBase + (pos << ArrayShift))
  }

  private def CAS(array: Array[AnyRef], pos: Int, ov: AnyRef, nv: AnyRef): Boolean = {
    unsafe.compareAndSwapObject(array, ArrayBase + (pos << ArrayShift), ov, nv)
  }

  private def READ_CACHE: Array[AnyRef] = rawCache

  private def CAS_CACHE(ov: Array[AnyRef], nv: Array[AnyRef]) = {
    unsafe.compareAndSwapObject(this, CacheTrieRawCacheOffset, ov, nv)
  }

  private def READ_WIDE(enode: ENode): Array[AnyRef] = enode.wide

  private def CAS_WIDE(enode: ENode, ov: Array[AnyRef], nv: Array[AnyRef]): Boolean = {
    unsafe.compareAndSwapObject(enode, ENodeWideOffset, ov, nv)
  }

  private def spread(h: Int): Int = {
    (h ^ (h >>> 16)) & 0x7fffffff
  }

  private[concurrent] def fastLookup(key: K): V = {
    val hash = spread(key.hashCode)
    fastLookup(key, hash)
  }

  private[concurrent] final def fastLookup(key: K, hash: Int): V = {
    val cache = READ_CACHE
    if (cache == null) {
      slowLookup(key, hash, 0, rawRoot, null, -1)
    } else {
      val len = cache.length
      val mask = len - 1 - 1
      val pos = 1 + (hash & mask)
      val cachee = READ(cache, pos)
      val level = 31 - Integer.numberOfLeadingZeros(len - 1) - 4 + 4
      if (cachee eq null) {
        // Nothing is cached at this location, do slow lookup.
        slowLookup(key, hash, 0, rawRoot, cachee, level)
      } else if (cachee.isInstanceOf[Array[AnyRef]]) {
        val an = cachee.asInstanceOf[Array[AnyRef]]
        val mask = an.length - 1
        val pos = (hash >>> level) & mask
        val old = READ(an, pos)
        if ((old eq null) || (old eq VNode)) {
          // The key is not present in the cache trie.
          null.asInstanceOf[V]
        } else if (old.isInstanceOf[SNode[_, _]]) {
          // Check if the key is contained in the single node.
          val oldsn = old.asInstanceOf[SNode[K, V]]
          val oldhash = oldsn.hash
          val oldkey = oldsn.key
          if ((oldhash == hash) && ((oldkey eq key) || (oldkey == key))) oldsn.value
          else null.asInstanceOf[V]
        } else {
          def resumeSlowLookup(): V = {
            if (old.isInstanceOf[Array[AnyRef]]) {
              // Continue the search from the specified level.
              val oldan = old.asInstanceOf[Array[AnyRef]]
              slowLookup(key, hash, level + 4, oldan, cachee, level)
            } else if ((old eq FVNode) || old.isInstanceOf[FNode]) {
              // Array node contains a frozen node, so it is obsolete -- do slow lookup.
              slowLookup(key, hash, 0, rawRoot, cachee, level)
            } else if (old.isInstanceOf[ENode]) {
              // Help complete the transaction.
              val en = old.asInstanceOf[ENode]
              completeExpansion(en)
              fastLookup(key, hash)
            } else {
              sys.error(s"Unexpected case -- $old")
            }
          }
          resumeSlowLookup()
        }
      } else {
        sys.error(s"Unexpected case -- $cachee is not supposed to be cached.")
      }
    }
  }

  private[concurrent] def debugReadCache: Array[AnyRef] = READ_CACHE

  private[concurrent] def debugReadRoot: Array[AnyRef] = rawRoot

  private[concurrent] def debugCachePopulate(level: Int, key: K, value: V): Unit = {
    rawCache = new Array[AnyRef](1 + (1 << level))
    rawCache(0) = new CacheNode(null, level)
    var i = 1
    while (i < rawCache.length) {
      val an = new Array[AnyRef](4)
      rawCache(i) = an
      var j = 0
      while (j < 4) {
        an(j) = new SNode(0, key, value)
        j += 1
      }
      i += 1
    }
  }

  final def apply(key: K): V = {
    val result = lookup(key)
    if (result.asInstanceOf[AnyRef] eq null) throw new NoSuchElementException
    else result
  }

  final def lookup(key: K): V = {
    val hash = spread(key.hashCode)
    fastLookup(key, hash)
  }

  private[concurrent] def slowLookup(key: K, hash: Int): V = {
    val node = rawRoot
    slowLookup(key, hash, 0, node, null, -1)
  }

  private[concurrent] def slowLookup(key: K): V = {
    val node = rawRoot
    val hash = spread(key.hashCode)
    slowLookup(key, hash, 0, node, null, -1)
  }

  @tailrec
  private[concurrent] final def slowLookup(
    key: K, hash: Int, level: Int, node: Array[AnyRef],
    cacheeSeen: AnyRef, cacheLevel: Int
  ): V = {
    if (level == cacheLevel) {
      populateCache(cacheeSeen, node, hash, level)
    }
    val mask = node.length - 1
    val pos = (hash >>> level) & mask
    val old = READ(node, pos)
    if ((old eq null) || (old eq VNode)) {
      null.asInstanceOf[V]
    } else if (old.isInstanceOf[Array[AnyRef]]) {
      val an = old.asInstanceOf[Array[AnyRef]]
      slowLookup(key, hash, level + 4, an, cacheeSeen, cacheLevel)
    } else if (old.isInstanceOf[SNode[_, _]]) {
      if (level != cacheLevel) {
        // A potential cache miss -- we need to check the cache state.
      }
      val oldsn = old.asInstanceOf[SNode[K, V]]
      if ((oldsn.hash == hash) && ((oldsn.key eq key) || (oldsn.key == key))) {
        oldsn.value
      } else {
        null.asInstanceOf[V]
      }
    } else if (old.isInstanceOf[LNode[_, _]]) {
      if (level != cacheLevel) {
        // A potential cache miss -- we need to check the cache state.
      }
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
    } else if (old.isInstanceOf[ENode]) {
      val enode = old.asInstanceOf[ENode]
      val narrow = enode.narrow
      slowLookup(key, hash, level + 4, narrow, cacheeSeen, cacheLevel)
    } else if (old eq FVNode) {
      null.asInstanceOf[V]
    } else if (old.isInstanceOf[FNode]) {
      val frozen = old.asInstanceOf[FNode].frozen
      if (frozen.isInstanceOf[SNode[_, _]]) {
        val sn = frozen.asInstanceOf[SNode[K, V]]
        if ((sn.hash == hash) && ((sn.key eq key) || (sn.key == key))) {
          sn.value
        } else {
          null.asInstanceOf[V]
        }
      } else if (frozen.isInstanceOf[LNode[_, _]]) {
        val ln = frozen.asInstanceOf[LNode[K, V]]
        if (ln.hash != hash) {
          null.asInstanceOf[V]
        } else {
          var tail = ln
          while (tail != null) {
            if ((tail.key eq key) || (tail.key == key)) {
              return tail.value
            }
            tail = tail.next
          }
          null.asInstanceOf[V]
        }
      } else if (frozen.isInstanceOf[Array[AnyRef]]) {
        val an = frozen.asInstanceOf[Array[AnyRef]]
        slowLookup(key, hash, level + 4, an, cacheeSeen, cacheLevel)
      } else {
        sys.error(s"Unexpected case: $old")
      }
    } else {
      sys.error(s"Unexpected case: $old")
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

  private[concurrent] final def slowInsert(key: K, value: V): Unit = {
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
          val enode = new ENode(parent, parentpos, current, hash, level)
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
        // TODO: Revisit when we start compressing nodes, right now it cannot happen.
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
    // If we succeeded, then we must update the cache.
    // Note that not all nodes will get cached from this site,
    // because some array nodes get created outside expansion
    // (e.g. when creating a node to resolve collisions in sequentialTransfer).
    if (CAS(parent, parentpos, enode, wide)) {
      populateCache(narrow, wide, enode.hash, level)
    }
  }

  @tailrec
  private def populateCache(
    ov: AnyRef, nv: AnyRef, hash: Int, cacheeLevel: Int
  ): Unit = {
    val cache = READ_CACHE
    if (cache eq null) {
      // Only create the cache if the entry is at least level 12,
      // since the expectation on the number of elements is ~80.
      // This means that we can afford to create a cache with 256 entries.
      if (cacheeLevel >= 12) {
        val cn = new Array[AnyRef](1 + (1 << 8))
        cn(0) = new CacheNode(null, 8)
        CAS_CACHE(null, cn)
        populateCache(ov, nv, hash, cacheeLevel)
      }
    } else {
      val len = cache.length
      val cacheLevel = Integer.numberOfTrailingZeros(len - 1)
      if (cacheeLevel == cacheLevel) {
        val mask = len - 1 - 1
        val pos = 1 + (hash & mask)
        val old = READ(cache, pos)
        if (old eq null) {
          // Nothing was ever written to the cache -- populate it.
          // Failure implies progress, in which case the new value is already stale.
          CAS(cache, pos, null, nv)
        } else if (old eq ov) {
          // We must replace the previous value with the new value.
          // Failure implies progress, in which case the new value is already stale.
          CAS(cache, pos, ov, nv)
        } else {
          // In all other cases, the new value is already stale, and we are done.
        }
      } else {
        // We have a cache level miss -- update statistics, and rebuild if necessary.
        // TODO
      }
    }
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
  private val CacheTrieRawCacheOffset = {
    val field = classOf[CacheTrie[_, _]].getDeclaredField("rawCache")
    Platform.unsafe.objectFieldOffset(field)
  }
  private val availableProcessors = Runtime.getRuntime.availableProcessors()

  /* result types */

  val Success = new AnyRef

  val Restart = new AnyRef

  /* node types */

  val VNode = new AnyRef

  class SNode[K <: AnyRef, V](
    val hash: Int,
    val key: K,
    val value: V
  ) {
    override def toString = s"SN[$hash, $key, $value]"
  }

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
    val hash: Int,
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

  class CacheNode(val parent: Array[AnyRef], val level: Int) {
    val missCounts = new Array[Int](availableProcessors * math.min(16, level))

    private def pos: Int = {
      val id = Thread.currentThread.getId
      val pos = (id ^ (id >>> 16)).toInt & (missCounts.length - 1)
      pos
    }

    final def approximateMissCount: Int = {
      missCounts(pos)
    }

    final def bumpMissCount(): Unit = {
      missCounts(pos) += 1
    }
  }
}
