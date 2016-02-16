package org.reactors



import com.typesafe.config._
import java.util.concurrent.atomic._
import scala.annotation.tailrec
import scala.collection._
import scala.collection.JavaConverters._
import org.reactors.common.Monitor
import org.reactors.concurrent._
import org.reactors.remoting.ReactorUrl
//import org.reactors.remoting.Remoting
import org.reactors.remoting.SystemUrl



/** A system used to create, track and identify reactors.
 *
 *  A reactor system is composed of a set of reactors that have
 *  a common configuration.
 *
 *  @param name      the name of this reactor system
 *  @param bundle    the scheduler bundle used by the reactor system
 */
class ReactorSystem(
  val name: String,
  val bundle: ReactorSystem.Bundle = ReactorSystem.defaultBundle
) extends concurrent.Services {

  /** Protects the internal state of the reactor system.
   */
  private val monitor = new Monitor

  /** Contains the frames for different reactors.
   */
  private[reactors] val frames = new UniqueStore[Frame]("reactor", monitor)

  /** Contains channels of various system reactors.
   */
  class StandardReactors {
    /** Replies to channel lookup requests.
     */
    // val resolver = {
    //   val p = Proto[Services.NameResolverReactor]
    //     .withName("resolver")
    //     .withChannelName("find")
    //   ReactorSystem.this.spawn(p)
    // }
  }

  /** Contains channels for standard reactors. */
  val iso = new StandardReactors

  /** Shuts down services. */
  def shutdown() {
    shutdownServices()
  }

  /** Creates a new reactor instance in this reactor system.
   *
   *  '''Use case:'''
   *  {{{
   *  def spawn(proto: Proto[Reactor[T]]): Channel[T]
   *  }}}
   *
   *  Implementations of this method must initialize the reactor frame with the
   *  `createFrame` method,
   *  add the reactor to the specific bookkeeping code,
   *  and then call the `wake` method on the reactor frame to start it for the first
   *  time.
   *  Finally, they must return the reactor's default channel.
   *
   *  @tparam T         the type of the events for the reactor
   *  @param p          the prototype for the reactor
   *  @param scheduler  the scheduler used to scheduler the reactor
   *  @return           the channel for this reactor
   */
  def spawn[@spec(Int, Long, Double) T: Arrayable](p: Proto[Reactor[T]]):
    Channel[T] = {
    tryCreateReactor(p)
  }

  protected[reactors] def tryCreateReactor[@spec(Int, Long, Double) T: Arrayable](
    proto: Proto[Reactor[T]]
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
      frame.url = ReactorUrl(bundle.urlsBySchema(proto.transport), uname)
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


/** Contains factory methods for creating reactor systems.
 */
object ReactorSystem {

  /** Creates the default reactor system.
   *  
   *  @param name       the name for the reactor system instance
   *  @param bundle     the reactor system bundle object
   *  @return           a new reactor system instance
   */
  def default(
    name: String, bundle: ReactorSystem.Bundle = ReactorSystem.defaultBundle
  ) =
    new ReactorSystem(name, bundle)

  /** Retrieves the default bundle config object.
   *
   *  This configuration is merged with any custom configurations that are provided to
   *  the reactor system bundle.
   */
  val defaultConfig: Config = {
    ConfigFactory.parseString("""
      remoting = {
        udp = {
          schema = "reactors.udp"
          host = "localhost"
          port = 17771
        }
        tcp = {
          schema = "reactors.tcp"
          host = "localhost"
          port = 17773
        }
      }
      system = {
        net = {
          parallelism = 8
        }
      }
    """)
  }

  /** Contains various configuration values related to the reactor system,
   *  such as the set of registered schedulers and the system url.
   */
  class Bundle(
    val defaultScheduler: Scheduler,
    private val customConfig: Config
  ) {
    private val schedulers = mutable.Map[String, Scheduler]()

    def this(s: Scheduler, config: String) = this(s, ConfigFactory.parseString(config))

    /** The set of configuration variables for the reactor system.
     */
    val config = customConfig.withFallback(defaultConfig)

    val urlsBySchema = config.getConfig("remoting").root.values.asScala.collect {
      case c: ConfigObject => c.toConfig
    } map { c =>
      (c.getString("schema"),
        SystemUrl(c.getString("schema"), c.getString("host"), c.getInt("port")))
    } toMap

    val urls = urlsBySchema.map(_._2).toSet

    /** Pickler implementation for this reactor system.
     */
    //val pickler = new Remoting.Pickler.JavaSerialization

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
     *  registered with the reactor system.
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
      val globalExecutionContext = "org.reactors.Scheduler.globalExecutionContext"
      val default = "org.reactors.Scheduler.default"
      val newThread = "org.reactors.Scheduler.newThread"
      val piggyback = "org.reactors.Scheduler.piggyback"
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

    /** Opens a new channel for this reactor.
     *
     *  @tparam Q        type of the events in the new channel
     *  @return          the connector object of the new channel
     */
    final def open[@spec(Int, Long, Double) Q: Arrayable]: Connector[Q] =
      Reactor.self.frame.openConnector[Q](channelName, eventQueueFactory, isDaemon)
  }
}
