package io.reactors
package protocol






/** Contains various convenience operations.
 */
trait ConvenienceProtocols {
  /** Adds convenience methods for reactor systems.
   */
  implicit def reactorSystemOps(system: ReactorSystem) =
    new Convenience.ReactorSystemOps(system)
}


object Convenience {
  /** Contains convenience methods for the `ReactorSystem` class.
   */
  class ReactorSystemOps(val system: ReactorSystem) {
    def spawnLocal[@spec(Int, Long, Double) T: Arrayable](
      body: Reactor[T] => Unit
    ): Channel[T] = {
      val proto = Reactor[T](body)
      system.spawn(proto)
    }
  }
}
