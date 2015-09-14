package scala.reactive
package isolate



import java.io._
import java.net.URL
import java.nio.charset.Charset
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
import scala.reflect.ClassTag
import scala.util.DynamicVariable
import scala.util.Success
import scala.util.Failure
import scala.util.Try



/** Contains services used by an isolate system.
 */
abstract class Services {
  system: IsoSystem =>

  private val extensions = mutable.Map[ClassTag[_], AnyRef]()

  /** System configuration */
  def config = system.bundle.config

  /** Clock services. */
  val clock = new Services.Clock(system)

  /** I/O services. */
  val io = new Services.Io(system)

  /** Network services. */
  val net = new Services.Net(system)

  /** Arbitrary service. */
  def service[T <: Protocol: ClassTag] = {
    val tag = implicitly[ClassTag[T]]
    if (!extensions.contains(tag)) {
      val ctor = tag.runtimeClass.getConstructor(classOf[IsoSystem])
      extensions(tag) = ctor.newInstance(system).asInstanceOf[AnyRef]
    }
    extensions(tag).asInstanceOf[T]
  }

}


/** Contains common service implementations.
 */
object Services {

  /** Contains I/O-related services.
   */
  class Io(val system: IsoSystem) extends Protocol {
    val defaultCharset = Charset.defaultCharset.name
  }

  /** Contains common network protocol services.
   */
  class Net(val system: IsoSystem, private val resolver: URL => InputStream)
  extends Protocol {
    private implicit val networkRequestPool: ExecutionContext = {
      val parallelism = system.config.getInt("system.net.parallelism")
      ExecutionContext.fromExecutor(new ForkJoinPool(parallelism))
    }

    def this(s: IsoSystem) = this(s, url => url.openStream())

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
        val connector = system.channels.daemon.open[Try[String]]
        Future {
          val inputStream = resolver(new URL(url))
          try {
            IOUtils.toString(inputStream, cs)
          } finally {
            inputStream.close()
          }
        } onComplete {
          case s @ Success(_) =>
            connector.channel ! s
            connector.seal()
          case f @ Failure(t) =>
            connector.channel ! f
            connector.seal()
        }
        connector.events.map({
          case Success(s) => s
          case Failure(t) => throw t
        })
      }

    }

  }

  /** Contains various time-related services.
   */
  class Clock(val system: IsoSystem)
  extends Protocol {
    private val timer = new Timer(s"${system.name}.timer-service", true)

    /** Emits an event periodically, with the duration between events equal to `d`.
     *
     *  Note that these events are fired eventually, and have similar semantics as that
     *  of `java.util.Timer`.
     *
     *  The channel through which the events arrive is daemon.
     *
     *  @param d        duration between events
     *  @param canLeak  the object that contains the leaky subscriptions
     *  @return         an event stream and subscription
     */
    def periodic(d: Duration)(implicit canLeak: CanLeak):
      Events[Unit] with Events.Subscription = {
      val connector = system.channels.daemon.open[Unit]
      val task = new TimerTask {
        def run() {
          connector.channel ! (())
        }
      }
      timer.schedule(task, d.toMillis, d.toMillis)
      val sub = Events.Subscription {
        task.cancel()
        connector.seal()
      }
      connector.events.withSubscription(sub)
    }

    /** Emits an event after a timeout specified by the duration `d`.
     *
     *  Note that this event is fired eventually after duration `d`, and has similar
     *  semantics as that of `java.util.Timer`.
     *
     *  The channel through which the event arrives is daemon.
     *
     *  @param d        duration after which the timeout event fires
     *  @param canLeak  the object that contains the leaky subscriptions
     *  @return         an event stream and subscription
     */
    def timeout(d: Duration)(implicit canLeak: CanLeak):
      Events[Unit] with Events.Subscription = {
      val connector = system.channels.daemon.open[Unit]
      val task = new TimerTask {
        def run() {
          connector.channel ! (())
        }
      }
      timer.schedule(task, d.toMillis)
      val sub = Events.Subscription {
        task.cancel()
        connector.seal()
      }
      connector.events.withSubscription(sub)
    }

  }

}
