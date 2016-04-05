package io.reactors
package common



import io.reactors.algebra._



/** Quad-based matrix for spatial querying of sparse data.
 */
class QuadMatrix[@specialized(Int, Long, Double) T](
  private[reactors] val blockExponent: Int = 8,
  private[reactors] val poolSize: Int = 32
)(
  implicit val arrayable: Arrayable[T]
) extends Matrix[T] {
  private[reactors] var blockSize: Int = _
  private[reactors] var blockMask: Int = _
  private[reactors] var removedValue: T = _
  private[reactors] var roots: HashMatrix[QuadMatrix.Node[T]] = _
  private[reactors] var empty: QuadMatrix.Node.Empty[T] = _
  private[reactors] var forkPool: FixedSizePool[QuadMatrix.Node.Fork[T]] = _
  private[reactors] var leafPool: FixedSizePool[QuadMatrix.Node.Leaf[T]] = _
  val nil = arrayable.nil

  private[reactors] def init(self: QuadMatrix[T]) {
    roots = new HashMatrix[QuadMatrix.Node[T]]
    blockSize = 1 << blockExponent
    blockMask = blockSize - 1
    empty = new QuadMatrix.Node.Empty[T]
    forkPool = new FixedSizePool(
      poolSize,
      () => QuadMatrix.Node.Fork.empty(this),
      n => n.clear(self))
    leafPool = new FixedSizePool(
      poolSize,
      () => QuadMatrix.Node.Leaf.empty[T],
      n => n.clear())
    removedValue = nil
  }
  init(this)

  private[reactors] def fillPools(self: QuadMatrix[T]) {
    var i = 0
    while (i < poolSize) {
      forkPool.release(QuadMatrix.Node.Fork.empty(self))
      leafPool.release(QuadMatrix.Node.Leaf.empty[T])
      i += 1
    }
  }

  def fillPools() {
    fillPools(this)
  }

  def update(gx: Int, gy: Int, v: T): Unit = {
    if (v == nil) remove(gx, gy)
    else {
      val bx = gx >> blockExponent
      val by = gy >> blockExponent
      val qx = gx & blockMask
      val qy = gy & blockMask
      var root = roots(bx, by)
      if (root == null) {
        root = empty
        roots(bx, by) = root
      }
      val nroot = root.update(qx, qy, v, blockExponent, this)
      if (root ne nroot) roots(bx, by) = nroot
    }
  }

  def remove(gx: Int, gy: Int): T = {
    val bx = gx >> blockExponent
    val by = gy >> blockExponent
    val qx = gx & blockMask
    val qy = gy & blockMask
    val root = roots(bx, by)
    if (root == null) nil
    else {
      val nroot = root.remove(qx, qy, blockExponent, this)
      if (nroot.isEmpty) roots.remove(bx, by)
      val prev = removedValue
      removedValue = nil
      prev
    }
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

  def foreach(f: XY => Unit): Unit = {
    for (rxy <- roots) {
      val root = roots(rxy.x, rxy.y)
      val x0 = rxy.x << blockExponent
      val y0 = rxy.y << blockExponent
      root.foreach(blockExponent, x0, y0, f)
    }
  }

  def copy(a: Array[T], gxf: Int, gyf: Int, gxu: Int, gyu: Int): Unit = ???

  def area(gxf: Int, gyf: Int, gxu: Int, gyu: Int): Matrix.Area[T] = ???

  def nonNilArea(gxf: Int, gyf: Int, gxu: Int, gyu: Int): Matrix.Area[T] = ???
}


object QuadMatrix {
  trait Node[@specialized(Int, Long, Double) T] {
    def isEmpty: Boolean
    def apply(x: Int, y: Int, exp: Int, self: QuadMatrix[T]): T
    def update(x: Int, y: Int, v: T, exp: Int, self: QuadMatrix[T]): Node[T]
    def remove(x: Int, y: Int, exp: Int, self: QuadMatrix[T]): Node[T]
    def foreach(exp: Int, x0: Int, y0: Int, f: XY => Unit): Unit
  }

  object Node {
    class Empty[@specialized(Int, Long, Double) T]
    extends Node[T] {
      def isEmpty = true
      def apply(x: Int, y: Int, exp: Int, self: QuadMatrix[T]): T =
        self.arrayable.nil
      def update(x: Int, y: Int, v: T, exp: Int, self: QuadMatrix[T]): Node[T] = {
        val leaf = self.leafPool.acquire()
        leaf.update(x, y, v, exp, self)
        leaf
      }
      def remove(x: Int, y: Int, exp: Int, self: QuadMatrix[T]): Node[T] = this
      def foreach(exp: Int, x0: Int, y0: Int, f: XY => Unit): Unit = {}
    }

    class Fork[@specialized(Int, Long, Double) T]
    extends Node[T] {
      private[reactors] var children: Array[Node[T]] = _

      private[reactors] def init(self: Fork[T]) {
        children = new Array(4)
      }
      init(this)

      def isEmpty = false

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
        val child = children(idx)
        val nchild = child.update(nx, ny, v, nexp, self)
        if (child ne nchild) {
          children(idx) = nchild
          child match {
            case l: Leaf[T] => self.leafPool.release(l)
            case f: Fork[T] => self.forkPool.release(f)
            case _ => // Not releasing Empty nodes.
          }
        }
        this
      }

      def remove(x: Int, y: Int, exp: Int, self: QuadMatrix[T]): Node[T] = {
        ???
      }

      def foreach(exp: Int, x0: Int, y0: Int, f: XY => Unit): Unit = {
        val nexp = exp - 1
        val m = 1 << nexp
        children(0).foreach(nexp, x0, y0, f)
        children(1).foreach(nexp, x0 + m, y0, f)
        children(2).foreach(nexp, x0, y0 + m, f)
        children(3).foreach(nexp, x0 + m, y0 + m, f)
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

      def isEmpty = false

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
          var fork: Node[T] = self.forkPool.acquire()
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

      def remove(x: Int, y: Int, exp: Int, self: QuadMatrix[T]): Node[T] = {
        val nil = self.nil
        var i = elements.length - 1
        var lasti = -1
        while (i >= 0) {
          val cx = coordinates(i * 2)
          val cy = coordinates(i * 2 + 1)
          if (cx == x && cy == y) {
            self.removedValue = elements(i)
            elements(i) = nil
            if (lasti != -1) {
              elements(i) = elements(lasti)
              coordinates(i * 2) = coordinates(lasti * 2)
              coordinates(i * 2 + 1) = coordinates(lasti * 2 + 1)
              return this
            } else {
              if (i == 0) return self.empty
              else return this
            }
          }
          if (lasti == -1) lasti = i
          i -= 1
        }
        return this
      }

      def clear() {
        val nil = arrayable.nil
        elements(0) = nil
        elements(1) = nil
        elements(2) = nil
        elements(3) = nil
      }

      def foreach(exp: Int, x0: Int, y0: Int, f: XY => Unit): Unit = {
        var i = 0
        val nil = arrayable.nil
        while (i < elements.length && elements(i) != nil) {
          val x = x0 + coordinates(2 * i)
          val y = y0 + coordinates(2 * i + 1)
          f(XY(x, y))
          i += 1
        }
      }
    }

    object Leaf {
      def empty[@specialized(Int, Long, Double) T: Arrayable] = new Leaf[T]
    }
  }
}
