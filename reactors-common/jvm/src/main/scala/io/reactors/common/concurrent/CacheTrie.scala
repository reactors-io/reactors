package io.reactors.common.concurrent



import sun.misc.Unsafe
import scala.annotation.switch
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

  private def READ_FREEZE(snode: SNode[_, _]): AnyRef = snode.freeze

  private def CAS_FREEZE(snode: SNode[_, _], nv: AnyRef): Boolean = {
    unsafe.compareAndSwapObject(snode, SNodeFrozenOffset, null, nv)
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
      } else if (cachee.isInstanceOf[SNode[_, _]]) {
        //println(s"$key - found single node cachee, cache level $level")
        val oldsn = cachee.asInstanceOf[SNode[K, V]]
        val reason = READ_FREEZE(oldsn)
        if (reason == null) {
          val oldhash = oldsn.hash
          val oldkey = oldsn.key
          if ((oldhash == hash) && ((oldkey eq key) || (oldkey == key))) oldsn.value
          else null.asInstanceOf[V]
        } else {
          // The single node is either frozen or scheduled for modification
          slowLookup(key, hash, 0, rawRoot, cachee, level)
        }
      } else if (cachee.isInstanceOf[Array[AnyRef]]) {
        //println(s"$key - found array cachee, cache level $level")
        val an = cachee.asInstanceOf[Array[AnyRef]]
        val mask = an.length - 1
        val pos = (hash >>> level) & mask
        val old = READ(an, pos)
        if ((old eq null) || (old eq VNode)) {
          // The key is not present in the cache trie.
          null.asInstanceOf[V]
        } else if (old.isInstanceOf[SNode[_, _]]) {
          val oldsn = old.asInstanceOf[SNode[K, V]]
          val reason = READ_FREEZE(oldsn)
          if (reason == null) {
            // The single node is up-to-date.
            // Check if the key is contained in the single node.
            val oldhash = oldsn.hash
            val oldkey = oldsn.key
            if ((oldhash == hash) && ((oldkey eq key) || (oldkey == key))) oldsn.value
            else null.asInstanceOf[V]
          } else {
            // The single node is either frozen or scheduled for modification.
            slowLookup(key, hash, 0, rawRoot, cachee, level)
          }
        } else {
          def resumeSlowLookup(): V = {
            if (old.isInstanceOf[Array[AnyRef]]) {
              // Continue the search from the specified level.
              val oldan = old.asInstanceOf[Array[AnyRef]]
              slowLookup(key, hash, level + 4, oldan, cachee, level)
            } else if (old.isInstanceOf[LNode[_, _]]) {
              // Check if the key is contained in the list node.
              var tail = old.asInstanceOf[LNode[K, V]]
              while (tail != null) {
                if ((tail.hash == hash) && ((tail.key eq key) || (tail.key == key))) {
                  return tail.value
                }
                tail = tail.next
              }
              null.asInstanceOf[V]
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

  private[concurrent] def debugCachePopulateTwoLevelSingle(
    level: Int, key: K, value: V
  ): Unit = {
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

  private[concurrent] def debugCachePopulateTwoLevel(
    level: Int, keys: Array[K], values: Array[V]
  ): Unit = {
    rawCache = new Array[AnyRef](1 + (1 << level))
    rawCache(0) = new CacheNode(null, level)
    var i = 1
    while (i < rawCache.length) {
      val an = new Array[AnyRef](4)
      rawCache(i) = an
      var j = 0
      while (j < 4) {
        an(j) = new SNode(0, keys(i * 4 + j), values(i * 4 + j))
        j += 1
      }
      i += 1
    }
  }

  private[concurrent] def debugCachePopulateOneLevel(
    level: Int, keys: Array[K], values: Array[V], scarce: Boolean
  ): Unit = {
    rawCache = new Array[AnyRef](1 + (1 << level))
    rawCache(0) = new CacheNode(null, level)
    var i = 1
    while (i < rawCache.length) {
      if (!scarce || i % 4 == 0) {
        rawCache(i) = new SNode(0, keys(i), values(i))
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
      if (level < cacheLevel || level >= cacheLevel + 8) {
        // A potential cache miss -- we need to check the cache state.
        recordCacheMiss()
      }
      val oldsn = old.asInstanceOf[SNode[K, V]]
      if (level + 4 == cacheLevel) {
        // println(s"about to populate for single node -- ${level + 4} vs $cacheLevel")
        populateCache(cacheeSeen, oldsn, hash, level + 4)
      }
      if ((oldsn.hash == hash) && ((oldsn.key eq key) || (oldsn.key == key))) {
        oldsn.value
      } else {
        null.asInstanceOf[V]
      }
    } else if (old.isInstanceOf[LNode[_, _]]) {
      if (level < cacheLevel || level >= cacheLevel + 8) {
        // A potential cache miss -- we need to check the cache state.
        recordCacheMiss()
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
      val reason = READ_FREEZE(oldsn)
      if (reason eq null) {
        // The node is not frozen or marked for 
        if ((oldsn.hash == hash) && ((oldsn.key eq key) || (oldsn.key == key))) {
          val sn = new SNode(hash, key, value)
          if (CAS_FREEZE(oldsn, sn)) {
            if (CAS(current, pos, oldsn, sn)) Success
            else slowInsert(key, value, hash, level, current, parent)
          } else slowInsert(key, value, hash, level, current, parent)
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
            val nnode = newNarrowOrWideNode(
              oldsn.hash, oldsn.key, oldsn.value, hash, key, value, level + 4)
            if (CAS_FREEZE(oldsn, nnode)) {
              if (CAS(current, pos, oldsn, nnode)) Success
              else slowInsert(key, value, hash, level, current, parent)
            } else slowInsert(key, value, hash, level, current, parent)
          }
        }
      } else if (reason eq FSNode) {
        // We landed into the middle of a transaction doing a freeze.
        // We must restart from the top, find the tranaction node and help.
        Restart
      } else {
        // The single node had been scheduled for replacement by some thread.
        // We need to help, then retry.
        CAS(current, pos, oldsn, reason)
        slowInsert(key, value, hash, level, current, parent)
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

  private def isFrozenS(n: AnyRef): Boolean = {
    if (n.isInstanceOf[SNode[_, _]]) {
      val f = READ_FREEZE(n.asInstanceOf[SNode[_, _]])
      f eq FSNode
    } else false
  }

  private def isFrozenA(n: AnyRef): Boolean = {
    n.isInstanceOf[FNode] && n.asInstanceOf[FNode].frozen.isInstanceOf[Array[AnyRef]]
  }

  private def isFrozenL(n: AnyRef): Boolean = {
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
        val sn = node.asInstanceOf[SNode[K, V]]
        val reason = READ_FREEZE(sn)
        if (reason == null) {
          // Freeze single node.
          // If it fails, then either someone helped or another txn is in progress.
          // If another txn is in progress, then we must reinspect the current slot.
          if (!CAS_FREEZE(node.asInstanceOf[SNode[K, V]], FSNode)) i -= 1
        } else if (reason eq FSNode) {
          // We can skip, another thread previously froze this node.
        } else  {
          // Another thread is trying to replace the single node.
          // In this case, we help and retry.
          CAS(current, i, node, reason)
          i -= 1
        }
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
      } else if ((node eq FVNode) || isFrozenL(node)) {
        // We can skip, another thread previously helped with freezing this node.
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
      val an = newNarrowOrWideNodeUsingFresh(oldsn, sn, level + 4)
      wide(pos) = an
    } else if (old.isInstanceOf[Array[AnyRef]]) {
      val oldan = old.asInstanceOf[Array[AnyRef]]
      val npos = (sn.hash >>> (level + 4)) & (oldan.length - 1)
      if (oldan(npos) == null) {
        oldan(npos) = sn
      } else if (oldan.length == 4) {
        val an = new Array[AnyRef](16)
        sequentialTransfer(oldan, an, level + 4)
        wide(pos) = an
        sequentialInsert(sn, wide, level, pos)
      } else {
        sequentialInsert(sn, oldan, level + 4, npos)
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
      } else if (isFrozenS(node)) {
        // We can copy it over to the wide node.
        val oldsn = node.asInstanceOf[SNode[K, V]]
        val sn = new SNode(oldsn.hash, oldsn.key, oldsn.value)
        val pos = (sn.hash >>> level) & mask
        if (wide(pos) == null) wide(pos) = sn
        else sequentialInsert(sn, wide, level, pos)
      } else if (isFrozenL(node)) {
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

  private def newNarrowOrWideNodeUsingFresh(
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
    h1: Int, k1: K, v1: V, h2: Int, k2: K, v2: V, level: Int
  ): AnyRef = {
    newNarrowOrWideNodeUsingFresh(new SNode(h1, k1, v1), new SNode(h2, k2, v2), level)
  }

  private def newListNarrowOrWideNode(
    oldln: CacheTrie.LNode[K, V], hash: Int, k: K, v: V, level: Int
  ): AnyRef = {
    var tail = oldln
    var ln: LNode[K, V] = null
    while (tail != null) {
      ln = new LNode(tail.hash, tail.key, tail.value, ln)
      tail = tail.next
    }
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

  def sampleAndUpdateCache(cache: Array[AnyRef], stats: CacheNode): Unit = {
    // Sample the hash trie to estimate the level distribution.
    // Use an 8-byte histogram, total sample size must be less than 255.
    var histogram = 0L
    val sampleSize = 128
    val sampleType = 2
    var seed = Thread.currentThread.getId + System.identityHashCode(this)
    val levelOffset = 4
    (sampleType: @switch) match {
      case 0 =>
        var i = 0
        while (i < sampleSize) {
          seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
          val hash = (seed >>> 16).toInt
          @tailrec
          def sampleHash(node: Array[AnyRef], level: Int, hash: Int): Int = {
            val mask = node.length - 1
            val pos = (hash >>> level) & mask
            val child = READ(node, pos)
            if (child.isInstanceOf[Array[AnyRef]]) {
              sampleHash(child.asInstanceOf[Array[AnyRef]], level + 4, hash)
            } else {
              level + levelOffset
            }
          }
          val level = sampleHash(rawRoot, 0, hash)
          val shift = (level >>> 2) << 3
          val addend = 1L << shift
          histogram += addend
          i += 1
        }
      case 1 =>
        var i = 0
        while (i < sampleSize) {
          seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
          val hash = (seed >>> 16).toInt
          def sampleKey(node: Array[AnyRef], level: Int, hash: Int): Int = {
            val mask = node.length - 1
            val pos = (hash >>> level) & mask
            var i = (pos + 1) % node.length
            while (i != pos) {
              val ch = READ(node, i)
              if (ch.isInstanceOf[SNode[_, _]] || isFrozenS(ch) || isFrozenL(ch)) {
                return level + levelOffset
              } else if (ch.isInstanceOf[Array[AnyRef]]) {
                val an = ch.asInstanceOf[Array[AnyRef]]
                val result = sampleKey(an, level + 4, hash)
                if (result != -1) return result
              } else if (isFrozenA(ch)) {
                val an = ch.asInstanceOf[FNode].frozen.asInstanceOf[Array[AnyRef]]
                val result = sampleKey(an, level + 4, hash)
                if (result != -1) return result
              }
              i = (i + 1) % node.length
            }
            -1
          }
          val level = sampleKey(rawRoot, 0, hash)
          if (level == -1) i = sampleSize
          else {
            val shift = (level >>> 2) << 3
            val addend = 1L << shift
            histogram += addend
            i += 1
          }
        }
      case 2 =>
        def count(histogram: Long): Int = {
          (
            ((histogram >>> 0) & 0xff) +
              ((histogram >>> 8) & 0xff) +
              ((histogram >>> 16) & 0xff) +
              ((histogram >>> 24) & 0xff) +
              ((histogram >>> 32) & 0xff) +
              ((histogram >>> 40) & 0xff) +
              ((histogram >>> 48) & 0xff) +
              ((histogram >>> 56) & 0xff)
            ).toInt
        }
        def sampleUnbiased(
          node: Array[AnyRef], level: Int, maxRepeats: Int, maxSamples: Int,
          startHistogram: Long, startSeed: Long
        ): Long = {
          var seed = startSeed
          var histogram = startHistogram
          val mask = node.length - 1
          var i = 0
          while (i < maxRepeats && count(histogram) < maxSamples) {
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            val hash = (seed >>> 16).toInt
            val pos = hash & mask
            val ch = READ(node, pos)
            if (ch.isInstanceOf[Array[AnyRef]]) {
              val an = ch.asInstanceOf[Array[AnyRef]]
              histogram += sampleUnbiased(
                an, level + 4, maxRepeats * 4, maxSamples, histogram, seed + 1
              )
            } else if (
              ch.isInstanceOf[SNode[_, _]] || isFrozenS(ch) || isFrozenL(ch)
            ) {
              val shift = ((level + levelOffset) >>> 2) << 3
              histogram += 1L << shift
            } else if (isFrozenA(ch)) {
              val an = ch.asInstanceOf[FNode].frozen.asInstanceOf[Array[AnyRef]]
              histogram += sampleUnbiased(
                an, level + 4, maxRepeats * 4, maxSamples, histogram, seed + 1
              )
            }
            i += 1
          }
          histogram
        }
        var i = 0
        val trials = 32
        while (i < trials) {
          seed += 1
          histogram += sampleUnbiased(rawRoot, 0, 1, sampleSize / trials, 0L, seed)
          i += 1
        }
    }

    // Find two consecutive levels with most elements.
    // Additionally, record the number of elements at the current cache level.
    val oldCacheLevel = stats.level
    var cacheCount = 0
    var bestLevel = 0
    var bestCount = (histogram & 0xff) + ((histogram >>> 8) & 0xff)
    var level = 8
    while (level < 64) {
      val count =
        ((histogram >>> level) & 0xff) + ((histogram >>> (level + 8)) & 0xff)
      if (count > bestCount) {
        bestCount = count
        bestLevel = level >> 1
      }
      if ((level >> 1) == oldCacheLevel) {
        cacheCount += count.toInt
      }
      level += 8
    }

    // Debug information.
    def printDebugInformation() {
      println(debugPerLevelDistribution)
      println(s"best level:  ${bestLevel} (count: $bestCount)")
      println(s"cache level: ${stats.level} (count: $cacheCount)")
      val histogramString =
        (0 until 8).map(_ * 8).map(histogram >>> _).map(_ & 0xff).mkString(",")
      println(histogramString)
      println(debugCacheStats)
      println()
    }

    // Decide whether to change the cache levels.
    val repairThreshold = 1.40f
    if (cacheCount * repairThreshold < bestCount) {
      //printDebugInformation()
      var currCache = cache
      var currStats = stats
      while (currStats.level > bestLevel) {
        // Drop cache level.
        val parentCache = currStats.parent
        if (CAS_CACHE(cache, parentCache)) {
          currCache = parentCache
          currStats = READ(currCache, 0).asInstanceOf[CacheNode]
        } else {
          // Bail out immediately -- cache will be repaired by someone else eventually.
          return
        }
      }
      while (currStats.level < bestLevel) {
        // Add cache level.
        val nextLevel = currStats.level + 4
        val nextLength = 1 + (1 << nextLevel)
        val nextCache = new Array[AnyRef](nextLength)
        nextCache(0) = new CacheNode(currCache, nextLevel)
        if (CAS_CACHE(cache, nextCache)) {
          currCache = nextCache
          currStats = READ(nextCache, 0).asInstanceOf[CacheNode]
        } else {
          // Bail our immediately -- cache will be repaired by someone else eventually.
          return
        }
      }
    }
  }

  private def recordCacheMiss(): Unit = {
    val missCountMax = 2048
    val cache = READ_CACHE
    if (cache ne null) {
      val stats = READ(cache, 0).asInstanceOf[CacheNode]
      if (stats.approximateMissCount > missCountMax) {
        // We must again check if the cache level is obsolete.
        // Reset the miss count.
        stats.resetMissCount()

        // Resample to find out if cache needs to be repaired.
        sampleAndUpdateCache(cache, stats)
      } else {
        stats.bumpMissCount()
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

  def debugCacheStats: String = {
    val cache = READ_CACHE
    val stats = cache(0).asInstanceOf[CacheNode]
    var size = 0
    for (i <- 1 until cache.length) {
      val c = cache(i)
      if (c != null && c != VNode && c != FVNode && !c.isInstanceOf[FNode]) {
        size += 1
      }
    }
    s"cache level: ${stats.level}, $size / ${cache.length - 1}"
  }

  def debugPerLevelDistribution: String = {
    val histogram = new Array[Int](10)
    var sz = 0
    def traverse(node: Array[AnyRef], level: Int): Unit = {
      var i = 0
      while (i < node.length) {
        val old = node(i)
        if (old.isInstanceOf[SNode[_, _]]) {
          histogram((level + 4) / 4) += 1
          sz += 1
        } else if (old.isInstanceOf[LNode[_, _]]) {
          var ln = old.asInstanceOf[LNode[_, _]]
          while (ln != null) {
            histogram((level + 4) / 4) += 1
            sz += 1
            ln = ln.next
          }
        } else if (old.isInstanceOf[Array[AnyRef]]) {
          val an = old.asInstanceOf[Array[AnyRef]]
          traverse(an, level + 4)
        } else if (old eq null) {
        } else {
          sys.error(s"Unexpected case: $old")
        }
        i += 1
      }
    }
    traverse(this.debugReadRoot, 0)
    val sb = new StringBuilder
    sb.append(s":: size $sz ::\n")
    for (i <- 0 until histogram.length) {
      val num = histogram(i)
      val percent = (100.0 * num / sz).toInt
      sb.append(f"${i * 4}%3d: $num%8d ($percent%3d%%) ${"*" * (num * 40 / sz)}\n")
    }
    sb.toString
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
  private val SNodeFrozenOffset = {
    val field = classOf[SNode[_, _]].getDeclaredField("freeze")
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

  object ANode {
    def toString(an: Array[AnyRef]) = an.mkString("AN[", ", ", "]")
  }

  class SNode[K <: AnyRef, V](
    @volatile var freeze: AnyRef,
    val hash: Int,
    val key: K,
    val value: V
  ) {
    def this(h: Int, k: K, v: V) = this(null, h, k, v)
    override def toString =
      s"SN[$hash, $key, $value, ${if (freeze != null) freeze else '_'}]"
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

  val FSNode = new AnyRef

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
      missCounts(0)
    }

    final def resetMissCount(): Unit = {
      missCounts(0) = 0
    }

    final def bumpMissCount(): Unit = {
      missCounts(0) += 1
    }
  }
}
