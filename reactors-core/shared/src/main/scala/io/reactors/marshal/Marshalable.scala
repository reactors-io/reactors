package io.reactors
package marshal





trait Marshalable {
  def marshal(data: Data): Unit
  def unmarshal(data: Data): Unit
}
