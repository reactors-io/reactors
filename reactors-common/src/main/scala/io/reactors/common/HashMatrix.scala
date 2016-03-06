package io.reactors
package common



import io.reactors.common.hash.byteswap32



class HashMatrix[@specialized(Int, Long, Double) T](
  private[reactors] val initialSize: Int = 16
)(
  implicit val arrayable: Arrayable[T]
) {
  private[reactors] var blocks = new Array[HashMatrix.Block[T]](initialSize)
  private[reactors] var numBlocks = 0

  private def hashblock(xb: Int, yb: Int): Int = {
    byteswap32(xb) ^ byteswap32(yb)
  }

  def apply(x: Int, y: Int): T = {
    val xb = x / 16
    val yb = y / 16
    val hash = hashblock(xb, yb)
    val idx = (hash & 0x7fffffff) % blocks.size
    var block = blocks(idx)

    while (block != null) {
      if (block.x == xb && block.y == yb) {
        val xm = x % 16
        val ym = y % 16
        return arrayable.apply(block.array, ym * 16 + xm)
      }
      block = block.next
    }

    arrayable.nil
  }

  def update(x: Int, y: Int, v: T): Unit = {
    val xb = x / 16
    val yb = y / 16
    val hash = hashblock(xb, yb)
    val idx = (hash & 0x7fffffff) % blocks.size
    var block = blocks(idx)

    while (block != null) {
      if (block.x == xb && block.y == yb) {
        val xm = x % 16
        val ym = y % 16
        arrayable.update(block.array, ym * 16 + xm, v)
        return
      }
      block = block.next
    }

    if (1.0 * numBlocks / blocks.length > HashMatrix.LOAD_FACTOR) {
      increaseSize()
    }

    block = new HashMatrix.Block(xb, yb, arrayable.newArray(16 * 16))
    block.next = blocks(idx)
    blocks(idx) = block
    numBlocks += 1
  }

  protected def increaseSize() {
    val nblocks = new Array[HashMatrix.Block[T]](blocks.size * 2)
    var i = 0
    while (i < blocks.length) {
      var block = blocks(i)
      while (block != null) {
        val nextblock = block.next
        val hash = hashblock(block.x, block.y)
        val idx = (hash & 0x7fffffff) % nblocks.size
        block.next = nblocks(idx)
        nblocks(idx) = block
        block = nextblock
      }
      i += 1
    }
    blocks = nblocks
  }

}


object HashMatrix {
  val LOAD_FACTOR = 0.45

  class Block[@specialized(Int, Long, Double) T](
    val x: Int,
    val y: Int,
    val array: Array[T]
  ) {
    var next: Block[T] = null
  }

}
