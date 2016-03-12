package io.reactors
package common



import io.reactors.common.hash.spatial2D
import scala.collection._



class HashMatrix[@specialized(Int, Long, Double) T](
  private[reactors] val initialSize: Int = 64
)(
  implicit val arrayable: Arrayable[T]
) {
  private[reactors] var blocks = new Array[HashMatrix.Block[T]](initialSize)
  private[reactors] var numBlocks = 0

  private def hashblock(xb: Int, yb: Int): Int = {
    //byteswap32(xb ^ yb)
    //byteswap32(xb) ^ byteswap32(yb)
    //(73856093 * byteswap32(xb)) ^ (83492791 * byteswap32(yb))
    spatial2D(xb, yb)
  }

  private[reactors] def debugBlockMap: Map[Int, List[HashMatrix.Block[T]]] = {
    val m = mutable.Map[Int, List[HashMatrix.Block[T]]]().withDefaultValue(List())
    for ((b, i) <- blocks.zipWithIndex) {
      var cur = b
      while (cur != null) {
        m(i) ::= cur
        cur = cur.next
      }
    }
    m
  }

  val nil = arrayable.nil

  def foreach(f: T => Unit): Unit = {
    var i = 0
    while (i < blocks.size) {
      var curr = blocks(i)
      while (curr != null) {
        var k = 0
        while (k < curr.array.length) {
          val v = curr.array(k)
          if (v != nil) f(v)
          k += 1
        }
        curr = curr.next
      }
      i += 1
    }
  }

  def apply(xr: Int, yr: Int): T = {
    val x = xr + (1 << 30)
    val y = yr + (1 << 30)
    val xb = x / 64
    val yb = y / 64
    val hash = hashblock(xb, yb)
    val idx = (hash & 0x7fffffff) % blocks.size

    var block = blocks(idx)
    while (block != null) {
      if (block.x == xb && block.y == yb) {
        val xm = x % 64
        val ym = y % 64
        return arrayable.apply(block.array, ym * 64 + xm)
      }
      block = block.next
    }

    nil
  }

  def update(x: Int, y: Int, v: T): Unit = applyAndUpdate(x, y, v)

  def applyAndUpdate(xr: Int, yr: Int, v: T): T = {
    val x = xr + (1 << 30)
    val y = yr + (1 << 30)
    val xb = x / 64
    val yb = y / 64
    val hash = hashblock(xb, yb)
    val idx = (hash & 0x7fffffff) % blocks.size

    var block = blocks(idx)
    while (block != null) {
      if (block.x == xb && block.y == yb) {
        val xm = x % 64
        val ym = y % 64
        val previous = arrayable.apply(block.array, ym * 64 + xm)
        arrayable.update(block.array, ym * 64 + xm, v)
        if (previous != nil && v == nil) block.nonNilCount -= 1
        if (previous == nil && v != nil) block.nonNilCount += 1
        return previous
      }
      block = block.next
    }

    if (1.0 * numBlocks / blocks.length > HashMatrix.LOAD_FACTOR) {
      increaseSize()
    }

    block = new HashMatrix.Block(xb, yb, arrayable.newArray(64 * 64))
    val xm = x % 64
    val ym = y % 64
    arrayable.update(block.array, ym * 64 + xm, v)
    block.nonNilCount += 1

    val nidx = (hash & 0x7fffffff) % blocks.size
    block.next = blocks(nidx)
    blocks(nidx) = block
    numBlocks += 1

    return nil
  }

  protected def increaseSize() {
    val nblocks = new Array[HashMatrix.Block[T]](blocks.size * 2)
    var i = 0
    var count = 0
    while (i < blocks.length) {
      var block = blocks(i)
      while (block != null) {
        count += 1
        val hash = hashblock(block.x, block.y)
        val idx = (hash & 0x7fffffff) % nblocks.size
        val nextblock = block.next
        block.next = nblocks(idx)
        nblocks(idx) = block
        block = nextblock
      }
      i += 1
    }
    blocks = nblocks
  }

  protected def clearSpecialized(self: HashMatrix[T]) {
    blocks = new Array[HashMatrix.Block[T]](initialSize)
    numBlocks = 0
  }

  def clear() = {
    clearSpecialized(this)
  }

}


object HashMatrix {
  val LOAD_FACTOR = 0.25

  class Block[@specialized(Int, Long, Double) T](
    val x: Int,
    val y: Int,
    val array: Array[T]
  ) {
    var nonNilCount = 0
    var next: Block[T] = null

    override def toString = s"Block($x, $y)"
  }

}
