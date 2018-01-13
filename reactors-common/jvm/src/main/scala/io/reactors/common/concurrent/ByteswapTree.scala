package io.reactors.common.concurrent



import sun.misc.Unsafe
import scala.annotation.switch
import scala.annotation.tailrec



class ByteswapTree[K <: AnyRef, V <: AnyRef] {
  import ByteswapTree._

  @volatile private var root: Node = new Leaf

  private def unsafe: Unsafe = Platform.unsafe

  private def READ_PERMUTATION(leaf: Leaf): Long = {
    unsafe.getLongVolatile(leaf, ???)
  }

  private def insert(k: K, v: V): Unit = {
    ???
  }

  private def insert(leaf: Leaf, k: K, v: V): Unit = {
    ???
  }
}


object ByteswapTree {
  private def layoutCheck(cls: Class[_], rootName: String): FieldOffsets = {
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

    FieldOffsets(firstOffset, scalingFactor)
  }

  case class FieldOffsets(start: Long, scalingFactor: Long)

  val leafEntryFields = layoutCheck(classOf[Leaf], "entry")
  val innerEntryFields = layoutCheck(classOf[Inner], "entry")
  val innerKeyFields = layoutCheck(classOf[Inner], "key")

  abstract class Node

  class Leaf extends Node {
    @volatile var permutation: Long = 0
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
    @volatile var permutation: Long = 0
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
}
