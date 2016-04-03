package io.reactors
package common



import io.reactors.algebra._



/** Quad-based matrix for spatial querying.
 */
class QuadMatrix[@specialized(Int, Long, Double) T](
  val blockExponent: Int = 8
)(
  implicit val arrayable: Arrayable[T]
) extends Matrix[T] {
  private[reactors] var blockSize: Int = _
  private[reactors] var blockMask: Int = _
  private[reactors] var roots: HashMatrix[QuadMatrix.Node[T]] = _
  private[reactors] var size: Int = _
  private[reactors] var empty: QuadMatrix.Node.Empty[T] = _
  val nil = arrayable.nil

  private[reactors] def init(self: QuadMatrix[T]) {
    roots = new HashMatrix[QuadMatrix.Node[T]]
    blockSize = 1 << blockExponent
    blockMask = blockSize - 1
    size = 0
    empty = new QuadMatrix.Node.Empty[T]
  }
  init(this)

  def update(gx: Int, gy: Int, v: T): Unit = {
    val bx = gx >> blockExponent
    val by = gy >> blockExponent
    val qx = gx & blockMask
    val qy = gy & blockMask
    var root = roots(bx, by)
    if (root == null) {
      root = new QuadMatrix.Node.Empty[T]
      roots(bx, by) = root
    }
    val nroot = root.update(qx, qy, v, blockExponent, this)
    if (root ne nroot) roots(bx, by) = nroot
  }

  def apply(gx: Int, gy: Int): T = {
    val bx = gx >> blockExponent
    val by = gy >> blockExponent
    val qx = gx & blockMask
    val qy = gy & blockMask
    val root = roots(bx, by)
    if (root == null) nil
    else root.apply(qx, qy, blockExponent, this)
  }

  def foreach(f: XY => Unit): Unit = ???

  def copy(a: Array[T], gxf: Int, gyf: Int, gxu: Int, gyu: Int): Unit = ???

  def area(gxf: Int, gyf: Int, gxu: Int, gyu: Int): Matrix.Area[T] = ???

  def nonNilArea(gxf: Int, gyf: Int, gxu: Int, gyu: Int): Matrix.Area[T] = ???
}


object QuadMatrix {
  trait Node[@specialized(Int, Long, Double) T] {
    def apply(x: Int, y: Int, exp: Int, self: QuadMatrix[T]): T
    def update(x: Int, y: Int, v: T, exp: Int, self: QuadMatrix[T]): Node[T]
  }

  object Node {
    class Empty[@specialized(Int, Long, Double) T]
    extends Node[T] {
      def apply(x: Int, y: Int, exp: Int, self: QuadMatrix[T]): T =
        self.arrayable.nil
      def update(x: Int, y: Int, v: T, exp: Int, self: QuadMatrix[T]): Node[T] = {
        val leaf = new Leaf[T]()(self.arrayable)
        leaf.update(x, y, v, exp, self)
        leaf
      }
    }

    class Fork[@specialized(Int, Long, Double) T]
    extends Node[T] {
      private[reactors] var children: Array[Node[T]] = _

      private[reactors] def init(self: Fork[T]) {
        children = new Array(4)
      }
      init(this)

      def clear(self: QuadMatrix[T]) {
        children(0) = self.empty
        children(1) = self.empty
        children(2) = self.empty
        children(3) = self.empty
      }

      def apply(x: Int, y: Int, exp: Int, self: QuadMatrix[T]): T = {
        val nexp = exp - 1
        val xidx = x >>> nexp
        val yidx = y >>> nexp
        val idx = (yidx << 1) + (xidx)
        val nx = x - (xidx << nexp)
        val ny = y - (yidx << nexp)
        children(idx).apply(nx, ny, nexp, self)
      }
      def update(x: Int, y: Int, v: T, exp: Int, self: QuadMatrix[T]): Node[T] = {
        val nexp = exp - 1
        val xidx = x >>> nexp
        val yidx = y >>> nexp
        val idx = (yidx << 1) + (xidx)
        val nx = x - (xidx << nexp)
        val ny = y - (yidx << nexp)
        children(idx) = children(idx).update(nx, ny, v, nexp, self)
        this
      }
    }

    object Fork {
      def empty[@specialized(Int, Long, Double) T](quad: QuadMatrix[T]) = {
        val fork = new Fork[T]
        fork.clear(quad)
        fork
      }
    }

    class Leaf[@specialized(Int, Long, Double) T](
      implicit val arrayable: Arrayable[T]
    ) extends Node[T] {
      private[reactors] var coordinates: Array[Int] = _
      private[reactors] var elements: Array[T] = _

      private[reactors] def init(self: Leaf[T]) {
        implicit val tag = arrayable.classTag
        coordinates = new Array(8)
        elements = arrayable.newArray(4)
      }
      init(this)

      def apply(x: Int, y: Int, exp: Int, self: QuadMatrix[T]): T = {
        var i = 0
        while (i < elements.length) {
          val cx = coordinates(i * 2)
          val cy = coordinates(i * 2 + 1)
          if (cx == x && cy == y) {
            return elements(i)
          }
          i += 1
        }
        self.arrayable.nil
      }
      def update(x: Int, y: Int, v: T, exp: Int, self: QuadMatrix[T]): Node[T] = {
        var i = 0
        val nil = self.arrayable.nil
        while (i < elements.length && elements(i) != nil) {
          val cx = coordinates(i * 2)
          val cy = coordinates(i * 2 + 1)
          if (cx == x && cy == y) {
            elements(i) = v
            return this
          }
          i += 1
        }
        if (i < elements.length) {
          elements(i) = v
          coordinates(i * 2) = x
          coordinates(i * 2 + 1) = y
          return this
        } else {
          assert(exp >= 2)
          var fork: Node[T] = Fork.empty(self)
          var i = 0
          while (i < elements.length) {
            val cx = coordinates(i * 2)
            val cy = coordinates(i * 2 + 1)
            val cv = elements(i)
            fork = fork.update(cx, cy, cv, exp, self)
            i += 1
          }
          fork = fork.update(x, y, v, exp, self)
          fork
        }
      }
    }
  }
}
