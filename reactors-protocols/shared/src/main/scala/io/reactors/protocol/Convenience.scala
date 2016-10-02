package io.reactors
package protocol






/** Contains various convenience operations.
 */
trait Convenience {
  implicit def reactorSystemOps(system: ReactorSystem) =
    new Convenience.ReactorSystemOps(system)
}


object Convenience {
  class ReactorSystemOps(val system: ReactorSystem) {
    def spawnLocal[@spec(Int, Long, Double) T: Arrayable](
      body: Reactor[T] => Unit
    ): Channel[T] = {
      val proto = Reactor[T](body)
      system.spawn(proto)
    }
  }
}
