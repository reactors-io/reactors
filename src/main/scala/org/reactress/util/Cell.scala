package org.reactress
package util






class Cell[@spec(Boolean, Int, Long, Double) T](private var value: T) {
  def apply() = value
  def :=(v: T) = value = v
}
