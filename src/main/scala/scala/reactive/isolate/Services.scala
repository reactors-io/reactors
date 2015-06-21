package scala.reactive
package isolate



import java.net.URL
import java.nio.charset.Charset
import java.io._
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic._
import org.apache.commons.io._
import scala.annotation.tailrec
import scala.collection._
import scala.concurrent._
import scala.concurrent.duration._
import scala.reactive.isolate._
import scala.util.DynamicVariable
import scala.util.Success
import scala.util.Failure



/** Contains services used by an isolate system.
 */
abstract class Services {
  system: IsoSystem =>

  /** System configuration */
  def config = system.bundle.config

  /** IO services */
  val io = new Services.Io(system)

  /** Network services. */
  val net = new Services.Net(system)

  /** Time services. */
  val time = new Services.Time(system)

}


/** Contains common service implementations.
 */
object Services {

  /** Contains I/O-related services.
   */
  class Io(val system: IsoSystem) {
    val defaultCharset = Charset.defaultCharset.name
  }

  /** Contains common network protocol services.
   */
  class Net(val system: IsoSystem) {
    private implicit val networkRequestPool: ExecutionContext = {
      val parallelism = system.config.getInt("system.net.parallelism")
      ExecutionContext.fromExecutor(new ForkJoinPool(parallelism))
    }

    /** Contains various methods used to retrieve remote resources.
     */
    object resource {

      /** Asynchronously retrieves the resource at the given URL.
       *
       *  Once the resource is retrieved, the resulting event stream emits an event with
       *  the string with the resource contents, and unreacts.
       *  In the case of failure, the event stream raises an exception and unreacts.
       *
       *  @param url     the url to load the resource from
       *  @param cs      the name of the charset to use
       *  @return        the event stream with the resource string
       */
      def string(url: String, cs: String = system.io.defaultCharset): Events[String] = {
        val connector = system.channels.daemon.open[String]
        Future {
          val inputStream = new URL(url).openStream()
          try {
            IOUtils.toString(inputStream, cs)
          } finally {
            inputStream.close()
          }
        } onComplete {
          case Success(s) =>
            connector.channel << s
            connector.channel.seal()
          case Failure(t) =>
            // TODO forward exception to connector.channel
            connector.channel.seal()
        }
        connector.events
      }

    }

  }

  /** Contains various time-related services.
   */
  class Time(val system: IsoSystem) {
    private val timer = new Timer(s"${system.name}.timer-service", true)

    /** Emits an event periodically, with the duration between events equal to `d`.
     *
     *  Note that these events are fired eventually, and have similar semantics as that
     *  of `java.util.Timer`.
     *
     *  The channel through which the events arrive is daemon.
     *
     *  @param d        duration between events
     *  @param canLeak  context in which 
     *  @return         an event stream and subscription
     */
    def period(d: Duration)(implicit canLeak: CanLeak):
      Events[Unit] with Events.Subscription = {
      val connector = system.channels.daemon.open[Unit]
      val task = new TimerTask {
        def run() {
          connector.channel << (())
        }
      }
      timer.schedule(task, d.toMillis, d.toMillis)
      val sub = Events.Subscription {
        task.cancel()
        connector.channel.seal()
      }
      connector.events.withSubscription(sub)
    }

  }

}
