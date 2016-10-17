package io.reactors



import scala.reflect.ClassTag



/** An encapsulation of a set of event streams and channels.
 */
trait Protocol {
  def system: ReactorSystem
}


object Protocol {
  /** A protocol that can be shut down. */
  trait Service extends Protocol with Platform.Reflectable {
    def shutdown(): Unit
  }
}