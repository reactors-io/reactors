package org



import scala.annotation.implicitNotFound
import scala.reflect.ClassTag



package object reactress extends ReactiveApi {

  type spec = specialized

  type XY = Long

  implicit class XYExtensions(val v: XY) extends AnyVal {
    final def x = XY.xOf(v)
    final def y = XY.yOf(v)
    final def +(that: XY) = XY(x + that.x, y + that.y)
    final def -(that: XY) = XY(x - that.x, y - that.y)
    final def *(v: Int) = XY(x * v, y * v)

    override def toString = "XY(%d, %d)".format(x, y)
  }

  object XY {
    final def xOf(v: Long) = (v & 0xffffffff).toInt
    final def yOf(v: Long) = (v >>> 32).toInt
    final def apply(x: Int, y: Int): XY = value(x, y)
    final def value(x: Int, y: Int): Long = (y.toLong << 32) | (x.toLong & 0xffffffffL)
    final def invalid = Long.MinValue
  }

  type ReactCell[T] = container.ReactCell[T]

  val ReactCell = container.ReactCell

  type ReactContainer[T] = container.ReactContainer[T]

  val ReactContainer = container.ReactContainer

  type ReactBuilder[T, Repr] = container.ReactBuilder[T, Repr]

  val ReactBuilder = container.ReactBuilder

  type ReactSet[T] = container.ReactSet[T]

  val ReactSet = container.ReactSet

  type ReactTable[K, V] = container.ReactTable[K, V]

  val ReactTable = container.ReactTable

  type ReactMap[K, V >: Null <: AnyRef] = container.ReactMap[K, V]

  val ReactMap = container.ReactMap

  // TODO reactive sorted set

  // TODO reactive sequence

  type ReactCatamorph[T, S] = container.ReactCatamorph[T, S]

  val ReactCatamorph = container.ReactCatamorph

  type CataMonoid[T, S] = container.CataMonoid[T, S]

  val CataMonoid = container.CataMonoid

  type CataCommutoid[T, S] = container.CataCommutoid[T, S]

  val CataCommutoid = container.CataCommutoid

  type CataBelian[T, S] = container.CataBelian[T, S]

  val CataBelian = container.CataBelian

  type ReactTileMap[T] = container.ReactTileMap[T]

  val ReactTileMap = container.ReactTileMap

  type CataSignaloid[T] = container.CataSignaloid[T]

  val CataSignaloid = container.CataSignaloid

  /* algebra */

  type Monoid[T] = algebra.Monoid[T]

  val Monoid = algebra.Monoid

  type Commutoid[T] = algebra.Commutoid[T]

  val Commutoid = algebra.Commutoid

  type Abelian[T] = algebra.Abelian[T]

  val Abelian = algebra.Abelian

  trait Foreach[@spec(Int, Long, Double) T] {
    def foreach[U](f: T => U): Unit
  }

  private[reactress] def nextPow2(num: Int): Int = {
    var v = num - 1
    v |= v >> 1
    v |= v >> 2
    v |= v >> 4
    v |= v >> 8
    v |= v >> 16
    v + 1
  }

  @implicitNotFound("This operation can buffer events, and may consume an arbitrary amount of memory. " +
    "Import the value `Permission.canBuffer` to allow buffering operations.")
  sealed trait CanBeBuffered

  object Permission {
    implicit def canBuffer = new CanBeBuffered {}
  }

  abstract class Arrayable[@spec(Int, Long) T] {
    val classTag: ClassTag[T]
    val nil: T
    def newArray(sz: Int): Array[T]
    def newNonNilArray(sz: Int): Array[T]
  }

  object Arrayable {
  
    implicit def arrayableRef[T >: Null <: AnyRef: ClassTag]: Arrayable[T] = new Arrayable[T] {
      val classTag = implicitly[ClassTag[T]]
      val nil = null
      def newArray(sz: Int) = new Array[T](sz)
      def newNonNilArray(sz: Int) = newArray(sz)
    }
  
    implicit val arrayableLong: Arrayable[Long] = new Arrayable[Long] {
      val classTag = implicitly[ClassTag[Long]]
      val nil = Long.MinValue
      def newArray(sz: Int) = Array.fill[Long](sz)(nil)
      def newNonNilArray(sz: Int) = new Array[Long](sz)
    }
  
    implicit val arrayableDouble: Arrayable[Double] = new Arrayable[Double] {
      val classTag = implicitly[ClassTag[Double]]
      val nil = Double.NaN
      def newArray(sz: Int) = Array.fill[Double](sz)(nil)
      def newNonNilArray(sz: Int) = new Array[Double](sz)
    }
  
    implicit val arrayableInt: Arrayable[Int] = new Arrayable[Int] {
      val classTag = implicitly[ClassTag[Int]]
      val nil = Int.MinValue
      def newArray(sz: Int) = Array.fill[Int](sz)(nil)
      def newNonNilArray(sz: Int) = new Array[Int](sz)
    }
  
  }

}


