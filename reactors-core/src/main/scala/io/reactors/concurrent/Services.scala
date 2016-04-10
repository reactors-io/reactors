package io.reactors
package concurrent



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
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.blocking
import scala.concurrent.duration._
//import io.reactors.remoting.Remoting
import scala.reflect.ClassTag
import scala.util.DynamicVariable
import scala.util.Success
import scala.util.Failure
import scala.util.Try



/** Defines services used by an isolate system.
 */
abstract class Services {
  def system: ReactorSystem

  private val services = mutable.Map[ClassTag[_], AnyRef]()

  /** System configuration */
  def config = system.bundle.config

  /** Clock services. */
  val clock = service[Services.Clock]

  /** I/O services. */
  val io = service[Services.Io]

  /** Naming services. */
  val names = service[Services.Names]

  /** Network services. */
  val net = service[Services.Net]

  /** Remoting services, used to contact other isolate systems. */
  //lazy val remoting = service[Remoting]

  /** The register of channels in this isolate system.
   *
   *  Used for creating and finding channels.
   */
  val channels: Services.Channels = service[Services.Channels]

  /** Arbitrary service. */
  def service[T <: Protocol.Service: ClassTag] = {
    val tag = implicitly[ClassTag[T]]
    if (!services.contains(tag)) {
      val ctor = tag.runtimeClass.getConstructor(classOf[ReactorSystem])
      services(tag) = ctor.newInstance(system).asInstanceOf[AnyRef]
    }
    services(tag).asInstanceOf[T]
  }

  /** Shut down all services. */
  def shutdownServices() {
    for ((_, service) <- services) {
      service.asInstanceOf[Protocol.Service].shutdown()
    }
  }

}


/** Contains common service implementations.
 */
object Services {

  /** Contains I/O-related services.
   */
  class Io(val system: ReactorSystem) extends Protocol.Service {
    val defaultCharset = Charset.defaultCharset.name

    def shutdown() {}
  }

  private[reactors] class NameResolverReactor
  extends Reactor[(String, Channel[Option[Channel[_]]])] {
    main.events onMatch {
      case (name, answer) => answer ! system.channels.find(name)
    }
  }

  /** Contains name resolution reactors.
   */
  class Names(val system: ReactorSystem) extends Protocol.Service {
    /** Replies to channel lookup requests.
     */
    lazy val resolve = {
      val p = Proto[NameResolverReactor]
        .withName("~names/resolve")
        .withChannelName("channel")
      system.spawn(p)
    }

    def shutdown() {
    }
  }

  /** Contains common network protocol services.
   */
  class Net(val system: ReactorSystem, private val resolver: URL => InputStream)
  extends Protocol.Service {
    private val networkRequestForkJoinPool = {
      val parallelism = system.config.getInt("system.net.parallelism")
      new ForkJoinPool(parallelism)
    }
    private implicit val networkRequestContext: ExecutionContext =
      ExecutionContext.fromExecutor(networkRequestForkJoinPool)

    def this(s: ReactorSystem) = this(s, url => url.openStream())

    def shutdown() {
      networkRequestForkJoinPool.shutdown()
    }

    /** Contains various methods used to retrieve remote resources.
     */
    object resource {

      /** Asynchronously retrieves the resource at the given URL.
       *
       *  Once the resource is retrieved, the resulting `IVar` gets a string event with
       *  the resource contents.
       *  In the case of failure, the event stream raises an exception and unreacts.
       *
       *  @param url     the url to load the resource from
       *  @param cs      the name of the charset to use
       *  @return        a single-assignment variable with the resource string
       */
      def asString(
        url: String, cs: String = system.io.defaultCharset
      ): IVar[String] = {
        val connector = system.channels.daemon.open[Try[String]]
        Future {
          blocking {
            val inputStream = resolver(new URL(url))
            try {
              IOUtils.toString(inputStream, cs)
            } finally {
              inputStream.close()
            }
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
        }).toIVar
      }

    }

  }

  /** Contains various time-related services.
   */
  class Clock(val system: ReactorSystem) extends Protocol.Service {
    private val timer = new Timer(s"reactors-io.${system.name}.timer-service", true)

    def shutdown() {
      timer.cancel()
    }

    /** Emits an event periodically, with the duration between events equal to `d`.
     *
     *  Note that these events are fired eventually, and have similar semantics as that
     *  of `java.util.Timer`.
     *
     *  The channel through which the events arrive is daemon.
     *
     *  @param d        duration between events
     *  @return         a signal and subscription
     */
    def periodic(d: Duration): Signal[Unit] = {
      val connector = system.channels.daemon.open[Unit]
      val task = new TimerTask {
        def run() {
          connector.channel ! (())
        }
      }
      timer.schedule(task, d.toMillis, d.toMillis)
      val sub = new Subscription {
        def unsubscribe() {
          task.cancel()
          connector.seal()
        }
      }
      connector.events.toSignal(()).withSubscription(sub)
    }

    /** Emits an event after a timeout specified by the duration `d`.
     *
     *  Note that this event is fired eventually after duration `d`, and has similar
     *  semantics as that of `java.util.Timer`.
     *
     *  The channel through which the event arrives is daemon.
     *
     *  @param d        duration after which the timeout event fires
     *  @return         a signal that emits the event on timeout
     */
    def timeout(d: Duration): Signal[Unit] = {
      val connector = system.channels.daemon.open[Unit]
      val task = new TimerTask {
        def run() {
          connector.channel ! (())
          connector.seal()
        }
      }
      timer.schedule(task, d.toMillis)
      val sub = new Subscription {
        def unsubscribe() {
          task.cancel()
          connector.seal()
        }
      }
      connector.events.toSignal(()).withSubscription(sub)
    }

    /** Emits an event at regular intervals, until the specified count reaches zero.
     *
     *  Note that this event is fired eventually after duration `d`, and has similar
     *  semantics as that of `java.util.Timer`.
     *
     *  The channel through which the event arrives is daemon.
     *
     *  Once the countdown reaches `0`, the resulting event stream unreacts, and the
     *  channel is sealed.
     *
     *  @param n        the starting value of the countdown
     *  @param d        period between countdowns
     *  @return         a signal with the countdown events
     */
    def countdown(n: Int, d: Duration): Signal[Int] = {
      assert(n > 0)
      val connector = system.channels.daemon.open[Int]
      val task = new TimerTask {
        var left = n
        def run() = if (left > 0) {
          left -= 1
          connector.channel ! left
          if (left == 0) {
            this.cancel()
            connector.seal()
          }
        }
      }
      timer.schedule(task, d.toMillis, d.toMillis)
      val sub = new Subscription {
        def unsubscribe() = {
          task.cancel()
          connector.seal()
        }
      }
      connector.events.toSignal(n).withSubscription(sub)
    }
  }

  /** The channel register used for channel lookup by name.
   *
   *  It can be used to query the channels in the local isolate system.
   *  To query channels in remote isolate systems, `Names` service should be used.
   */
  class Channels(val system: ReactorSystem)
  extends ReactorSystem.ChannelBuilder(null, false, EventQueue.UnrolledRing.Factory)
  with Protocol.Service {
    def shutdown() {
    }

    /** Optionally returns the channel with the given name, if it exists.
     *
     *  @param name      name of the isolate and channel, separate with a `#` character
     */
    def find[T](name: String): Option[Channel[T]] = {
      val parts = name.split("#")
      find[T](parts(0), parts(1))
    }

    /** Optionally returns the channel with the given name, if it exists.
     *
     *  @param isoName      name of the isolate
     *  @param channelName  name of the channel
     */
    def find[T](isoName: String, channelName: String): Option[Channel[T]] = {
      val frame = system.frames.forName(isoName)
      if (frame == null) None
      else {
        val conn = frame.connectors.forName(channelName)
        if (conn == null) None
        else Some(conn.channel.asInstanceOf[Channel[T]])
      }
    }
  }
}
