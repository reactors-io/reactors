package io.reactors
package marshal





trait Marshalable {
  def marshal(buffer: AnyRef): Unit
  def unmarshal(buffer: AnyRef): Unit
}
