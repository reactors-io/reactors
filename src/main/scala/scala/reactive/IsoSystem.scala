package scala.reactive



import com.typesafe.config._
import java.util.concurrent.atomic._
import scala.annotation.tailrec
import scala.collection._
import scala.reactive.isolate._
import scala.reactive.remoting.SystemUrl
import scala.reactive.remoting.IsoUrl
import scala.reactive.util.Monitor



/** A system used to create, track and identify isolates.
 *
 *  An isolate system is composed of a set of isolates that have
 *  a common configuration.
 *
 *  @param name      the name of this isolate system
 *  @param bundle    the scheduler bundle used by the isolate system
 */
class IsoSystem(
  val name: String,
  val bundle: IsoSystem.Bundle = IsoSystem.defaultBundle
) extends isolate.Services {

  /** Protects the internal state of the isolate system.
   */
  private val monitor = new Monitor

  /** Contains the frames for different isolates.
   */
  private[reactive] val frames = new UniqueStore[Frame]("isolate", monitor)

  /** Contains channels of various system isolates.
   */
  class StandardIsolates {
    /** Replies to channel lookup requests.
     */
    val resolver = {
      val p = Proto[Services.NameResolverIso]
        .withName("resolver")
        .withChannelName("find")
      isolate(p)
    }
  }

  /** Contains channels for standard isolates. */
  val iso = new StandardIsolates

  /** Creates a new isolate instance in this isolate system.
   *
   *  '''Use case:'''
   *  {{{
   *  def isolate(proto: Proto[Iso[T]]): Channel[T]
   *  }}}
   *
   *  Implementations of this method must initialize the isolate frame with the
   *  `createFrame` method,
   *  add the isolate to the specific bookkeeping code,
   *  and then call the `wake` method on the isolate frame to start it for the first
   *  time.
   *  Finally, they must return the isolate's default channel.
   *
   *  @tparam T         the type of the events for the isolate
   *  @param p          the prototype for the isolate
   *  @param scheduler  the scheduler used to scheduler the isolate
   *  @return           the channel for this isolate
   */
  def isolate[@spec(Int, Long, Double) T: Arrayable](p: Proto[Iso[T]]): Channel[T] = {
    tryCreateIsolate(p)
  }

  protected[reactive] def tryCreateIsolate[@spec(Int, Long, Double) T: Arrayable](
    proto: Proto[Iso[T]]
  ): Channel[T] = {
    // 1. ensure a unique id
    val uid = frames.reserveId()
    val scheduler = proto.scheduler match {
      case null => bundle.defaultScheduler
      case name => bundle.scheduler(name)
    }
    val factory = proto.eventQueueFactory match {
      case null => EventQueue.UnrolledRing.Factory
      case fact => fact
    }
    assert(proto.channelName != "system")
    val frame = new Frame(uid, proto, scheduler, this)

    // 2. reserve the unique name or break
    val uname = frames.tryStore(proto.name, frame)

    try {
      // 3. allocate the standard connectors
      frame.name = uname
      frame.url = IsoUrl(bundle.url, uname)
      frame.defaultConnector = frame.openConnector[T](proto.channelName, factory, false)
      frame.internalConnector = frame.openConnector[SysEvent]("system", factory, true)

      // 4. schedule for the first execution
      scheduler.startSchedule(frame)
      frame.scheduleForExecution()
    } catch {
      case t: Throwable =>
        // 5. if not successful, release the name and rethrow
        frames.tryRelease(uname)
        throw t
    }

    // 6. return the default channel
    frame.defaultConnector.channel.asInstanceOf[Channel[T]]
  }

}


/** Contains factory methods for creating isolate systems.
 */
object IsoSystem {

  /** Creates the default isolate system.
   *  
   *  @param name       the name for the isolate system instance
   *  @param bundle     the isolate system bundle object
   *  @return           a new isolate system instance
   */
  def default(name: String, bundle: IsoSystem.Bundle = IsoSystem.defaultBundle) =
    new IsoSystem(name, bundle)

  /** Retrieves the default bundle config object.
   *
   *  This configuration is merged with any custom configurations that are provided to
   *  the isolate system bundle.
   */
  val defaultConfig: Config = {
    ConfigFactory.parseString("""
      remoting = {
        url = {
          schema = ""
          host = "localhost"
          port = 17172
        }
      }
      system = {
        net = {
          parallelism = 8
        }
      }
    """)
  }

  /** Contains various configuration values related to the isolate system,
   *  such as the set of registered schedulers and the system url.
   */
  class Bundle(
    val defaultScheduler: Scheduler,
    private val customConfig: Config
  ) {
    private val schedulers = mutable.Map[String, Scheduler]()

    def this(s: Scheduler, config: String) = this(s, ConfigFactory.parseString(config))

    /** The set of configuration variables for the isolate system.
     */
    val config = customConfig.withFallback(defaultConfig)

    /** Url that resolves to this isolate system.
     */
    val url = SystemUrl(
      config.getString("remoting.url.schema"),
      config.getString("remoting.url.host"),
      config.getInt("remoting.url.port"))

    /** Retrieves the scheduler registered under the specified name.
     *  
     *  @param name        the name of the scheduler
     *  @return            the scheduler object associated with the name
     */
    def scheduler(name: String): Scheduler = {
      schedulers(name)
    }

    /** Does an inverse lookup for the name of this scheduler instance.
     *  The method fails if this specific scheduler instance was not previously
     *  registered with the isolate system.
     *
     *  @param scheduler           scheduler that was previously registered
     *  @return                    name of the previously registered scheduler
     */
    def schedulerName(s: Scheduler): String = {
      schedulers.find(_._2 eq s).get._1
    }

    /** Registers the scheduler under a specific name,
     *  so that it can be later retrieved using the 
     *  `scheduler` method.
     *
     *  @param name       the name under which to register the scheduler
     *  @param s          the scheduler object to register
     */
    def registerScheduler(name: String, s: Scheduler) {
      if (schedulers contains name) sys.error(s"Scheduler $name already registered.")
      else schedulers(name) = s
    }
  }

  /** Scheduler bundle factory methods.
   */
  object Bundle {
    object schedulers {
      val globalExecutionContext = "scala.reactive.Scheduler.globalExecutionContext"
      val default = "scala.reactive.Scheduler.default"
      val newThread = "scala.reactive.Scheduler.newThread"
      val piggyback = "scala.reactive.Scheduler.piggyback"
    }

    /** A bundle with default schedulers from the `Scheduler` companion object.
     *  
     *  @return           the default scheduler bundle
     */
    def default(default: Scheduler): Bundle = {
      val b = new Bundle(default, ConfigFactory.empty)
      b.registerScheduler(schedulers.globalExecutionContext,
        Scheduler.globalExecutionContext)
      b.registerScheduler(schedulers.default, Scheduler.default)
      b.registerScheduler(schedulers.newThread, Scheduler.newThread)
      b.registerScheduler(schedulers.piggyback, Scheduler.piggyback)
      b
    }
  }

  /** Default scheduler bundle.
   */
  lazy val defaultBundle = Bundle.default(Scheduler.default)

  class ChannelBuilder(
    val channelName: String,
    val isDaemon: Boolean,
    val eventQueueFactory: EventQueue.Factory
  ) {
    /** Associates a new name for the channel.
     */
    def named(name: String) = new ChannelBuilder(name, isDaemon, eventQueueFactory)

    /** Specifies a daemon channel.
     */
    def daemon = new ChannelBuilder(channelName, true, eventQueueFactory)

    /** Associates a new event queue factory.
     */
    def eventQueue(factory: EventQueue.Factory) =
      new ChannelBuilder(channelName, isDaemon, factory)

    /** Opens a new channel for this isolate.
     *
     *  @tparam Q        type of the events in the new channel
     *  @return          the connector object of the new channel
     */
    final def open[@spec(Int, Long, Double) Q: Arrayable]: Connector[Q] =
      Iso.self.frame.openConnector[Q](channelName, eventQueueFactory, isDaemon)
  }
}
