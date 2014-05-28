package org.reactress
package container



import scala.reflect.ClassTag



class ReactTileMap[@spec(Int, Long, Double) T: ClassTag](
  private[reactress] var dim: Int,
  private[reactress] val dflt: T,
  private[reactress] val compress: Boolean = true
) extends ReactContainer[(Int, Int, T)] with ReactBuilder[(Int, Int, T), ReactTileMap[T]] with ReactContainer.Default[(Int, Int, T)] {
  self =>

  import ReactTileMap._

  private var pow2size = 0
  private var previous: Ref[T] = null
  private var sz = 0
  private[reactress] var hiddenRoot: Node[T] = null
  private[reactress] var valueContainer: EmitContainer[T] = null
  private[reactress] var insertsEmitter: Reactive.Emitter[(Int, Int, T)] = null
  private[reactress] var removesEmitter: Reactive.Emitter[(Int, Int, T)] = null
  private[reactress] var updatesEmitter: Reactive.Emitter[XY] = null
  private[reactress] var dimensionsEmitter: Reactive.Emitter[Int] = null
  private[reactress] def quadRoot = root

  protected def root: Node[T] = hiddenRoot

  protected def root_=(r: Node[T]) = hiddenRoot = r

  protected def checkRoot(d: T) {
    // due to a bug in specialization, to ensure proper
    // specialized field is initialized
    if (hiddenRoot == null) {
      init(d)
    }
  }

  protected def init(d: T) {
    // we do this due to a bug in specialization
    // field writes in the constructor end up to the generic field
    // instead of a spec one, resulting in an IllegalAccessError
    pow2size = nextPow2(dim)
    previous = new Ref[T]
    hiddenRoot = new Node.Leaf(d)
    valueContainer = new EmitContainer[T](f => foreachNonDefault(0, 0, dim, dim)(new Applier[T] {
      def apply(x: Int, y: Int, elem: T) = f(elem)
    }), () => size)
    insertsEmitter = new Reactive.Emitter[(Int, Int, T)]
    removesEmitter = new Reactive.Emitter[(Int, Int, T)]
    updatesEmitter = new Reactive.Emitter[XY]
    dimensionsEmitter = new Reactive.Emitter[Int]
  }

  init(dflt)

  def builder: ReactBuilder[(Int, Int, T), ReactTileMap[T]] = this

  def +=(kv: (Int, Int, T)) = {
    if (kv._1 >= pow2size || kv._2 >= pow2size) dimension = math.max(kv._1, kv._2)
    update(kv._1, kv._2, kv._3)
    true
  }

  def -=(kv: (Int, Int, T)) = {
    update(kv._1, kv._2, dflt)
    true
  }

  def container = this
  
  def values: ReactContainer[T] = valueContainer

  def updates: Reactive[XY] = {
    checkRoot(dflt)
    updatesEmitter
  }

  def dimensions: Reactive[Int] = {
    checkRoot(dflt)
    dimensionsEmitter
  }

  def inserts: Reactive[(Int, Int, T)] = {
    checkRoot(dflt)
    insertsEmitter
  }

  def removes: Reactive[(Int, Int, T)] = {
    checkRoot(dflt)
    removesEmitter
  }

  def default = dflt

  def size = sz

  @inline final def contains(x: Int, y: Int) = x >= 0 && x < dim && y >= 0 && y < dim

  def leaf(x: Int, y: Int) = root.leaf(x, y, pow2size)

  def apply(x: Int, y: Int): T = {
    checkRoot(dflt)
    root.apply(x, y, pow2size)
  }

  def clamp(x: Int, y: Int) = if (contains(x, y)) apply(x, y) else {
    val xt = if (x < 0) 0 else if (x >= dim) dim - 1 else x
    val yt = if (y < 0) 0 else if (y >= dim) dim - 1 else y
    apply(xt, yt)
  }

  def orElse(x: Int, y: Int, elem: T) = {
    if (contains(x, y)) apply(x, y) else elem
  }

  def read(array: Array[T], width: Int, height: Int, fromx: Int, fromy: Int, untilx: Int, untily: Int) {
    checkRoot(dflt)
    root.read(array, width, height, fromx, fromy, untilx, untily, 0, 0, pow2size)
  }

  def update(xy: XY, elem: T): Unit = this(xy.x, xy.y) = elem

  def update(x: Int, y: Int, elem: T): Unit = {
    require(contains(x, y))
    checkRoot(dflt)
    
    val old = root
    root = root.update(x, y, pow2size, implicitly[ClassTag[T]], default, elem, compress, previous)
    
    val prevelem = previous.elem
    val prevDefault = prevelem == dflt
    val currDefault = elem == dflt

    if (prevDefault && !currDefault) sz += 1
    else if (!prevDefault && currDefault) sz -= 1

    if (!prevDefault) {
      valueContainer.removes += prevelem
      if (removesEmitter.hasSubscriptions) {
        removesEmitter += (x, y, prevelem)
      }
    }

    if (!currDefault) {
      valueContainer.inserts += elem
      if (insertsEmitter.hasSubscriptions) {
        insertsEmitter += (x, y, elem)
      }
    }
    updatesEmitter += XY(x, y)
  }

  def updateSafe(x: Int, y: Int, elem: T): Unit = {
    if (contains(x, y)) update(x, y, elem)
  }
  
  def foreachLeaf[U](f: T => U): Unit = root.foreachLeaf(f)

  def foreachNonDefault[U](fromx: Int, fromy: Int, untilx: Int, untily: Int)(t: Applier[T]): Unit = {
    checkRoot(dflt)
    def clamp(v: Int) = if (v < 0) 0 else if (v >= dim) dim - 1 else v
    root.foreachNonDefault[U](clamp(fromx), clamp(fromy), clamp(untilx), clamp(untily), 0, 0, pow2size, dflt)(t)
  }

  def foreach(f: ((Int, Int, T)) => Unit) = foreachNonDefault(0, 0, dim, dim)(new Applier[T] {
    def apply(x: Int, y: Int, elem: T) = f((x, y, elem))
  })

  protected def clearSafe(dummy: T) = {
    checkRoot(dflt)

    val oldroot = root
    root = new Node.Leaf(dflt)

    if (removesEmitter.hasSubscriptions) {
      oldroot.foreachNonDefault(0, 0, dim, dim, 0, 0, dim, dflt)(new Applier[T] {
        def apply(x: Int, y: Int, elem: T) = {
          valueContainer.removes += elem
          removesEmitter += (x, y, elem)
        }
      })
    }
  }

  def clear() = clearSafe(dflt)

  def dimension = dim

  def dimension_=(ndim: Int) = setDimensionSafe(ndim, dflt)

  protected def setDimensionSafe(ndim: Int, dummy: T) {
    checkRoot(dflt)

    val npow2size = nextPow2(ndim)
    val nroot = {
      var x, y = 0
      var nr: Node[T] = new Node.Leaf(default)
      val ylimit = math.min(ndim, dim)
      val xlimit = math.min(ndim, dim)
      while (y < ylimit) {
        while (x < xlimit) {
          val elem = apply(x, y)
          nr = nr.update(x, y, npow2size, implicitly[ClassTag[T]], default, elem, compress, previous)
          x += 1
        }
        y += 1
      }
      nr
    }
    val oldpow2size = pow2size
    val oldroot = root
    val olddim = dim

    dim = ndim
    pow2size = npow2size
    root = nroot

    dimensionsEmitter += ndim

    if (ndim < olddim && removesEmitter.hasSubscriptions) {
      var x, y = 0
      while (y < olddim) {
        while (x < olddim) {
          if (x >= ndim || y >= ndim) {
            val elem = oldroot.apply(x, y, oldpow2size)
            if (elem != dflt) removesEmitter += (x, y, elem)
          }
          x += 1
        }
        y += 1
      }
    }
  }

}


object ReactTileMap {

  trait Applier[@spec(Int, Long, Double) T] {
    def apply(x: Int, y: Int, elem: T): Unit
  }

  sealed trait Event
  case object NoEvent extends Event
  case class Resize(dim: Int) extends Event
  case class Update(x: Int, y: Int) extends Event
  case object Clear extends Event

  trait DefaultValue[@spec(Int, Long, Double) S] {
    def apply(): S
  }

  def DefaultValue[@spec(Int, Long, Double) S](v: S) = new DefaultValue[S] {
    def apply() = v
  }

  def apply[@spec(Int, Long, Double) T: ClassTag](size: Int, default: T) = new ReactTileMap[T](size, default)

  implicit def factory[@spec(Int, Long, Double) S](implicit d: DefaultValue[S], ct: ClassTag[S]) = new ReactBuilder.Factory[(Int, Int, S), ReactTileMap[S]] {
    def apply() = new ReactTileMap[S](1, d())
  }

  private[reactress] class Ref[@spec(Int, Long, Double) T] {
    var elem: T = _
  }

  /* implementation */

  final def matrixSize = 4

  trait Node[@spec(Int, Long, Double) T] {
    def apply(x: Int, y: Int, dim: Int): T
    def read(array: Array[T], width: Int, height: Int, fromx: Int, fromy: Int, untilx: Int, untily: Int, offsetx: Int, offsety: Int, dim: Int): Unit
    def update(x: Int, y: Int, dim: Int, tag: ClassTag[T], d: T, elem: T, compress: Boolean, previous: Ref[T]): Node[T]
    def foreachLeaf[U](f: T => U): Unit
    def foreachNonDefault[U](fromx: Int, fromy: Int, untilx: Int, untily: Int, offsetx: Int, offsety: Int, dim: Int, dflt: T)(f: Applier[T]): Unit
    def leaf(x: Int, y: Int, dim: Int): Node[T]
    def isLeaf: Boolean = false
    def asLeaf: Node.Leaf[T] = ???
  }

  object Node {
    final case class Leaf[@spec(Int, Long, Double) T](val element: T) extends Node[T] {
      def apply(x: Int, y: Int, dim: Int) = element
      def update(x: Int, y: Int, dim: Int, tag: ClassTag[T], d: T, elem: T, compress: Boolean, previous: Ref[T]) = if (element == elem) this else {
        if (dim > matrixSize) {
          val fork = new Fork[T](
            new Leaf(element),
            new Leaf(element),
            new Leaf(element),
            new Leaf(element)
          )
          fork.update(x, y, dim, tag, d, elem, compress, previous)
        } else {
          val matrix = new Matrix[T](tag.newArray(matrixSize * matrixSize))
          matrix.fill(element)
          matrix.update(x, y, dim, tag, d, elem, compress, previous)
        }
      }
      def read(array: Array[T], width: Int, height: Int, fromx: Int, fromy: Int, untilx: Int, untily: Int, offsetx: Int, offsety: Int, dim: Int) {
        val xlimit = math.min(offsetx + dim, untilx)
        val ylimit = math.min(offsety + dim, untily)
        var x = math.max(offsetx, fromx)
        while (x < xlimit) {
          var y = math.max(offsety, fromy)
          while (y < ylimit) {
            array(width * (y - fromy) + (x - fromx)) = element
            y += 1
          }
          x += 1
        }
      }
      def foreachLeaf[U](f: T => U) {
        f(element)
      }
      def foreachNonDefault[U](fromx: Int, fromy: Int, untilx: Int, untily: Int, offsetx: Int, offsety: Int, dim: Int, dflt: T)(f: Applier[T]) {
        if (element != dflt) {
          var x = offsetx
          while (x < offsetx + dim) {
            var y = offsety
            while (y < offsety + dim) {
              f(x, y, element)
              y += 1
            }
            x += 1
          }
        }
      }
      def leaf(x: Int, y: Int, dim: Int): Node[T] = {
        assert(dim > matrixSize, dim)
        this
      }
      override def isLeaf = true
      override def asLeaf = this.asInstanceOf[Leaf[T]]
    }

    final case class Fork[@spec(Int, Long, Double) T](
      var nw: Node[T],
      var ne: Node[T],
      var sw: Node[T],
      var se: Node[T]
    ) extends Node[T] {
      def apply(x: Int, y: Int, dim: Int) = {
        val xmid = dim / 2
        val ymid = dim / 2
        if (x < xmid) {
          if (y < ymid) sw.apply(x, y, dim / 2)
          else nw.apply(x, y - ymid, dim / 2)
        } else {
          if (y < ymid) se.apply(x - xmid, y, dim / 2)
          else ne.apply(x - xmid, y - ymid, dim / 2)
        }
      }
      def read(array: Array[T], width: Int, height: Int, fromx: Int, fromy: Int, untilx: Int, untily: Int, offsetx: Int, offsety: Int, dim: Int) {
        val xmid = offsetx + dim / 2
        val ymid = offsety + dim / 2
        if (fromx < xmid) {
          if (fromy < ymid) sw.read(array, width, height, fromx, fromy, untilx, untily, offsetx, offsety, dim / 2)
          if (untily > ymid) nw.read(array, width, height, fromx, fromy, untilx, untily, offsetx, ymid, dim / 2)
        }
        if (untilx > xmid) {
          if (fromy < ymid) se.read(array, width, height, fromx, fromy, untilx, untily, xmid, offsety, dim / 2)
          if (untily > ymid) ne.read(array, width, height, fromx, fromy, untilx, untily, xmid, ymid, dim / 2)
        }
      }
      def update(x: Int, y: Int, dim: Int, tag: ClassTag[T], d: T, elem: T, compress: Boolean, previous: Ref[T]) = {
        val xmid = dim / 2
        val ymid = dim / 2

        if (x < xmid) {
          if (y < ymid) sw = sw.update(x, y, dim / 2, tag, d, elem, compress, previous)
          else nw = nw.update(x, y - ymid, dim / 2, tag, d, elem, compress, previous)
        } else {
          if (y < ymid) se = se.update(x - xmid, y, dim / 2, tag, d, elem, compress, previous)
          else ne = ne.update(x - xmid, y - ymid, dim / 2, tag, d, elem, compress, previous)
        }

        if (compress && nw.isLeaf && ne.isLeaf && sw.isLeaf && se.isLeaf) {
          val nwe = nw.asLeaf.element
          val nee = ne.asLeaf.element
          val swe = sw.asLeaf.element
          val see = se.asLeaf.element
          if (nwe == nee && nwe == swe && nwe == see) new Leaf(nwe)
          else this
        } else this
      }
      def foreachLeaf[U](f: T => U) {
        nw.foreachLeaf(f)
        ne.foreachLeaf(f)
        sw.foreachLeaf(f)
        se.foreachLeaf(f)
      }
      def foreachNonDefault[U](fromx: Int, fromy: Int, untilx: Int, untily: Int, offsetx: Int, offsety: Int, dim: Int, dflt: T)(f: Applier[T]) {
        val xmid = offsetx + dim / 2
        val ymid = offsety + dim / 2
        if (fromx < xmid) {
          if (fromy < ymid) sw.foreachNonDefault(fromx, fromy, untilx, untily, offsetx, offsety, dim / 2, dflt)(f)
          if (untily > ymid) nw.foreachNonDefault(fromx, fromy, untilx, untily, offsetx, ymid, dim / 2, dflt)(f)
        }
        if (untilx > xmid) {
          if (fromy < ymid) se.foreachNonDefault(fromx, fromy, untilx, untily, xmid, offsety, dim / 2, dflt)(f)
          if (untily > ymid) ne.foreachNonDefault(fromx, fromy, untilx, untily, xmid, ymid, dim / 2, dflt)(f)
        }
      }
      def leaf(x: Int, y: Int, dim: Int): Node[T] = {
        val xmid = dim / 2
        val ymid = dim / 2
        if (x < xmid) {
          if (y < ymid) sw.leaf(x, y, dim / 2)
          else nw.leaf(x, y - ymid, dim / 2)
        } else {
          if (y < ymid) se.leaf(x - xmid, y, dim / 2)
          else ne.leaf(x - xmid, y - ymid, dim / 2)
        }
      }
    }

    final case class Matrix[@spec(Int, Long, Double) T](val array: Array[T]) extends Node[T] {
      def apply(x: Int, y: Int, dim: Int) = {
        array(y * matrixSize + x)
      }
      def read(output: Array[T], width: Int, height: Int, fromx: Int, fromy: Int, untilx: Int, untily: Int, offsetx: Int, offsety: Int, dim: Int) {
        val xlimit = math.min(offsetx + dim, untilx)
        val ylimit = math.min(offsety + dim, untily)
        var y = math.max(offsety, fromy)
        while (y < ylimit) {
          var x = math.max(offsetx, fromx)
          while (x < xlimit) {
            val element = array((y - offsety) * matrixSize + (x - offsetx))
            output(width * (y - fromy) + (x - fromx)) = element
            x += 1
          }
          y += 1
        }
      }
      private def allSame(elem: T): Boolean = {
        var i = 1
        while (i < matrixSize * matrixSize) {
          if (elem != array(i)) return false
          i += 1
        }
        true
      }
      def fill(elem: T) {
        var i = 0
        while (i < array.length) {
          array(i) = elem
          i += 1
        }
      }
      def update(x: Int, y: Int, dim: Int, tag: ClassTag[T], d: T, elem: T, compress: Boolean, previous: Ref[T]) = {
        val prev = array(y * matrixSize + x)
        array(y * matrixSize + x) = elem
        previous.elem = prev

        if (compress && allSame(array(0))) new Leaf(elem)
        else this
      }
      def foreachLeaf[U](f: T => U) {
        var x = 0
        while (x < matrixSize) {
          var y = 0
          while (y < matrixSize) {
            f(array(y * matrixSize + x))
            y += 1
          }
          x += 1
        }
      }
      def foreachNonDefault[U](fromx: Int, fromy: Int, untilx: Int, untily: Int, offsetx: Int, offsety: Int, dim: Int, dflt: T)(f: Applier[T]) {
        val xlimit = math.min(offsetx + dim, untilx)
        val ylimit = math.min(offsety + dim, untily)
        var y = math.max(offsety, fromy)
        while (y < ylimit) {
          var x = math.max(offsetx, fromx)
          while (x < xlimit) {
            val element = array((y - offsety) * matrixSize + (x - offsetx))
            if (element != dflt) f(x, y, element)
            x += 1
          }
          y += 1
        }
      }
      def leaf(x: Int, y: Int, dim: Int): Node[T] = this
    }
  }

}

