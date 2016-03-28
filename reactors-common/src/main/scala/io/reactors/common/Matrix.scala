package io.reactors
package common



import io.reactors.algebra.XY
import io.reactors.common.hash.spatial2D
import scala.collection._



trait Matrix[@specialized(Int, Long, Double) T] {
  def apply(x: Int, y: Int): T
  def update(x: Int, y: Int, v: T): Unit
}
