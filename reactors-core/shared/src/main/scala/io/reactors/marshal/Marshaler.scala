package io.reactors
package marshal





trait Marshaler[@spec(Int, Long, Double) T] {
  def marshal(x: T, buffer: AnyRef): Unit
  def unmarshal(buffer: AnyRef): T
}
