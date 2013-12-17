package org






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

  type Arrayable[T] = container.Arrayable[T]

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

  type ReactMap[K, V] = container.ReactMap[K, V]

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

}


