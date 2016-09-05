package io.reactors
package protocol



import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try



/** Contains various convenience operations.
 */
trait Convenience {
  implicit def reactorSystemOps(system: ReactorSystem) =
    new Convenience.ReactorSystemOps(system)
}


object Convenience {
  class ReactorSystemOps(val system: ReactorSystem) {
    def spawn[@spec(Int, Long, Double) T](body: Reactor[T] => Unit): Channel[T] = {
      val proto = Reactor[R](body)
      system.spawn(body)
    }
  }
}
