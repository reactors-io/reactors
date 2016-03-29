package io.reactors
package common



import io.reactors.algebra.XY
import io.reactors.common.hash.spatial2D
import scala.collection._



trait Matrix[@specialized(Int, Long, Double) T] extends Matrix.Immutable[T] {
  def update(x: Int, y: Int, v: T): Unit
}


object Matrix {
  trait Immutable[@specialized(Int, Long, Double) T] {
    def apply(x: Int, y: Int): T
    def copy(a: Array[T], gxf: Int, gyf: Int, gxu: Int, gyu: Int): Unit
    def area(gxf: Int, gyf: Int, gxu: Int, gyu: Int): Matrix.Area[T]
    def nonNilArea(gxf: Int, gyf: Int, gxu: Int, gyu: Int): Matrix.Area[T]
    def foreach(f: XY => Unit): Unit
  }

  trait Action[@specialized(Int, Long, Double) T] {
    def apply(x: Int, y: Int, v: T): Unit
  }

  trait Area[@specialized(Int, Long, Double) T] {
    def foreach(a: Action[T]): Unit
  }
}
