package scala.reactive
package isolate



import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic._
import scala.annotation.tailrec
import scala.collection._
import scala.concurrent._
import scala.concurrent.duration._
import scala.io.BufferedSource
import scala.io.Source
import scala.reactive.isolate._
import scala.util.DynamicVariable



/** Contains services used by an isolate system.
 */
abstract class Services {
  system: IsoSystem =>

  /** System configuration */
  def config = system.bundle.config

  /** Network services. */
  val net = new Services.Net(system)

  /** Time services. */
  val time = new Services.Time(system)

}


/** Contains common service implementations.
 */
object Services {

  /** Contains common network protocol services.
   */
  class Net(val system: IsoSystem) {
    private implicit val networkRequestPool: ExecutionContext = {
      val parallelism = system.config.getInt("system.net.parallelism")
      ExecutionContext.fromExecutor(new ForkJoinPool(parallelism))
    }

    /** Asynchronously retrieves the resource at the given URL.
     *  Once the resource is retrieved, the resulting event stream emits an event with
     *  the `BufferedSource` object, and unreacts.
     */
    def url(path: String): Events[BufferedSource] = {
      val connector = system.channels.open[BufferedSource]
      Future {
        Source.fromURL(path)
      } foreach {
        s =>
        connector.channel << s
        connector.channel.seal()
      }
      connector.events
    }

  }

  /** Contains various time-related services.
   */
  class Time(val system: IsoSystem) {

    /** Emits an event periodically, every second.
     */
    def period(d: Duration): Events[Unit] = ???

  }

}
