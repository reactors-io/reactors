package io.reactors






/** Any object that contains a unique id within some scope.
 */
trait Identifiable {
  def uid: Long
}
