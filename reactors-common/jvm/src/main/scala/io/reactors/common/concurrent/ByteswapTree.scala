package io.reactors.common.concurrent



import sun.misc.Unsafe
import scala.annotation.switch
import scala.annotation.tailrec



class ByteswapTree[K <: AnyRef: Ordering[K], V <: AnyRef] {
  import ByteswapTree._

  private val ordering = implicitly[Ordering[K]]
  @volatile private var root: Node = new Leaf

  private def unsafe: Unsafe = Platform.unsafe

  private def SLOT_MASK: Long = 0xfL

  private def COUNT_SHIFT: Int = 60

  private def SHIFT_ALL_MASK: Long = 0x0111111111111111L

  private def READ_MASK(leaf: Leaf): Long = {
    unsafe.getLongVolatile(leaf, LeafMaskOffset)
  }

  private def CAS_PERMUTATION(leaf: Leaf, ov: Long, nv: Long): Boolean = {
    unsafe.compareAndSwapLong(leaf, LeafMaskOffset, ov, nv)
  }

  private def READ_ENTRY(leaf: Leaf, idx: Int): Entry[K, V] = {
    unsafe.getObjectVolatile(leaf, LeafEntryOffset + idx * LeafEntryScaling)
      .asInstanceOf[Entry[K, V]]
  }

  private def CAS_ENTRY(
    leaf: Leaf, idx: Int, ov: Entry[K, V], nv: Entry[K, V]
  ): Boolean = {
    unsafe.compareAndSwapObject(leaf, LeafEntryOffset + idx * LeafEntryScaling, ov, nv)
  }

  private def insert(k: K, v: V): Unit = {
    ???
  }

  private def insert(leaf: Leaf, k: K, v: V): Unit = {
    // Determine node state.
    val mask = READ_MASK(leaf)
    val count = (mask >>> COUNT_SHIFT).toInt
    val removedCount = Integer.numberOfLeadingZeros(~(mask << 4)) >> 2

    // Determine the position for the key, and whether to replace an old key.
    var existing = false
    var left = 0
    var right = count - removedCount - 1
    while (left <= right) {
      val m = (left + right) >> 1
      val midx = (mask >>> (m << 2)) & SLOT_MASK
      val entry = READ_ENTRY(leaf, midx)
      val comparison = ordering.compare(entry.key, k)
      if (comparison < 0) left = m + 1
      else if (comparison > 0) right = m - 1
      else {
        left = m
        right = -1
        existing = k == entry.key
      }
    }

    // Determine the new mask.
    var newMask: Long = 0L
    if (existing) {
      newMask |= 
      newMask |= 
      newMask |= 
      newMask |= 
      newMask |= 
    } else {
      newMask |= 
      newMask |= 
      newMask |= 
      newMask |= 
      newMask |= 
    }

    // Attempt to propose the next entry.
    val entry = new Entry(k, v)
    if (CAS_ENTRY(leaf, count, null, entry)) {
      // Try to commit the proposed entry.
      ???
    } else {
      // Help complete an already proposed key.
      ???

      // Retry.
    }
  }
}


object ByteswapTree {
  private def offset(cls: Class[_], fieldName: String): Long = {
    val field = cls.getDeclaredField(fieldName)
    Platform.unsafe.objectFieldOffset(field)
  }

  private[concurrent] val LeafMaskOffset = offset(classOf[Leaf], "mask")
  private[concurrent] val LeafEntryOffset = offset(classOf[Leaf], "entry0")
  private[concurrent] val LeafEntryScaling =
    offset(classOf[Leaf], "entry1") - offset(classOf[Leaf], "entry0")
  private[concurrent] val InnerMaskOffset = offset(classOf[Inner], "mask")
  private[concurrent] val InnerEntryOffset = offset(classOf[Inner], "entry0")
  private[concurrent] val InnerEntryScaling =
    offset(classOf[Inner], "entry1") - offset(classOf[Inner], "entry0")
  private[concurrent] val InnerKeyOffset = offset(classOf[Inner], "key0")
  private[concurrent] val InnerKeyScaling =
    offset(classOf[Inner], "key1") - offset(classOf[Inner], "key0")

  private def layoutCheck(cls: Class[_], rootName: String): Unit = {
    def offset(fieldName: String): Long = {
      val field = cls.getDeclaredField(fieldName)
      Platform.unsafe.objectFieldOffset(field)
    }

    val firstOffset = offset(rootName + "0")
    val scalingFactor = offset(rootName + "1") - firstOffset

    def compareOffsets(idx: Int, fieldName: String): Unit = {
      val entryOffset = offset(fieldName)
      val expectedOffset = firstOffset + idx * scalingFactor
      if (entryOffset != expectedOffset) {
        throw new RuntimeException(
          s"Assumptions about layout are not met by this VM in $cls. " +
          s"Field $fieldName is at offset $entryOffset, expected $expectedOffset.")
      }
    }

    for (i <- 1 until 15) {
      compareOffsets(i, rootName + i)
    }
  }

  layoutCheck(classOf[Leaf], "entry")
  layoutCheck(classOf[Inner], "entry")
  layoutCheck(classOf[Inner], "key")

  abstract class Node

  class Leaf extends Node {
    @volatile var mask: Long = 0
    @volatile var unused0: AnyRef = null
    @volatile var entry0: AnyRef = null
    @volatile var entry1: AnyRef = null
    @volatile var entry2: AnyRef = null
    @volatile var entry3: AnyRef = null
    @volatile var entry4: AnyRef = null
    @volatile var entry5: AnyRef = null
    @volatile var entry6: AnyRef = null
    @volatile var entry7: AnyRef = null
    @volatile var entry8: AnyRef = null
    @volatile var entry9: AnyRef = null
    @volatile var entry10: AnyRef = null
    @volatile var entry11: AnyRef = null
    @volatile var entry12: AnyRef = null
    @volatile var entry13: AnyRef = null
    @volatile var entry14: AnyRef = null
  }

  class Inner extends Node {
    @volatile var mask: Long = 0
    @volatile var unused0: AnyRef = null
    @volatile var entry0: AnyRef = null
    @volatile var entry1: AnyRef = null
    @volatile var entry2: AnyRef = null
    @volatile var entry3: AnyRef = null
    @volatile var entry4: AnyRef = null
    @volatile var entry5: AnyRef = null
    @volatile var entry6: AnyRef = null
    @volatile var entry7: AnyRef = null
    @volatile var entry8: AnyRef = null
    @volatile var entry9: AnyRef = null
    @volatile var entry10: AnyRef = null
    @volatile var entry11: AnyRef = null
    @volatile var entry12: AnyRef = null
    @volatile var entry13: AnyRef = null
    @volatile var entry14: AnyRef = null
    @volatile var key0: AnyRef = null
    @volatile var key1: AnyRef = null
    @volatile var key2: AnyRef = null
    @volatile var key3: AnyRef = null
    @volatile var key4: AnyRef = null
    @volatile var key5: AnyRef = null
    @volatile var key6: AnyRef = null
    @volatile var key7: AnyRef = null
    @volatile var key8: AnyRef = null
    @volatile var key9: AnyRef = null
    @volatile var key10: AnyRef = null
    @volatile var key11: AnyRef = null
    @volatile var key12: AnyRef = null
    @volatile var key13: AnyRef = null
    @volatile var key14: AnyRef = null
  }

  class Entry[K <: AnyRef, V <: AnyRef](val key: K, val value: V)
}
