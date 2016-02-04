package org.reactors



import org.reactors.common._



/** A special type of an event stream that caches the last emitted event.
 *
 *  This last event is called the signal's ''value''.
 *  It can be read using the `Signal`'s `apply` method.
 *  
 *  @tparam T        the type of the events in this signal
 */
trait Signal[@spec(Int, Long, Double) T] extends Events[T] {

  /** Returns the last event produced by `this` signal.
   *
   *  
   *
   *  @return         the signal's value
   *  @throws         `NoSuchElementException` if the signal does not contain an event
   */
  def apply(): T

}