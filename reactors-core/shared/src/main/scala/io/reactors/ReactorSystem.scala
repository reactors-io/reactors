package io.reactors



import io.reactors.common.Monitor
import io.reactors.concurrent._
import io.reactors.pickle.Pickler
import java.util.Timer
import java.util.concurrent.atomic._
import scala.annotation.tailrec
import scala.collection._



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
) extends Services {

  def system = this

  private[reactors] val globalTimer = new Timer(s"reactors-io.$name.global-timer", true)

  /** Protects the internal state of the reactor system.
   */
  private[reactors] val monitor = new Monitor

  /** Debugging API.
   */
  private[reactors] val debugApi: DebugApi = {
    Platform.Reflect.instantiate(bundle.config.string("debug-api.name"), Seq(this))
      .asInstanceOf[DebugApi]
  }

  /** Error handler used to report uncaught exceptions.
   */
  private[reactors] val errorHandler: ErrorHandler = {
    Platform.Reflect.instantiate(bundle.config.string("error-handler.name"), Seq())
      .asInstanceOf[ErrorHandler]
  }

  /** Contains the frames for different reactors.
   */
  private[reactors] val frames = new ScalableUniqueStore[Frame.Info]("reactor", 512)

  /** Shuts down services. */
  def shutdown() {
    debugApi.shutdown()
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
  def spawn[@spec(Int, Long, Double) T: Arrayable](p: Proto[Reactor[T]]): Channel[T] = {
    trySpawnReactor(p)
  }

  protected[reactors] def trySpawnReactor[@spec(Int, Long, Double) T: Arrayable](
    proto: Proto[Reactor[T]]
  ): Channel[T] = {
    // 1. Ensure a unique id.
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

    // 2. Reserve the unique name or break.
    var uname: String = null
    while (uname == null) {
      if (proto.name == null) {
        // If choosing any name, it will always be fresh.
        uname = frames.tryStore(null, Frame.Info(frame, immutable.Map()))
      } else {
        // If choosing a specific name, it could already have channel listeners.
        val info = frames.forName(proto.name)
        if (info == null) {
          uname = frames.tryStore(proto.name, Frame.Info(frame, immutable.Map()))
        } else {
          if (info.isEmpty) {
            val ninfo = Frame.Info(frame, info.connectors)
            if (frames.tryReplace(proto.name, info, ninfo)) {
              uname = proto.name
            }
          } else exception.illegalArg(s"Reactor '${proto.name}' already exists.")
        }
      }
    }

    try {
      // 3. Allocate the standard connectors.
      frame.name = uname
      frame.url = ReactorUrl(bundle.urlMap(proto.transportOrDefault(this)).url, uname)

      // 4. Prepare for the first execution.
      scheduler.initSchedule(frame)

      // 5. Prepare pre-ctor: create standard connectors,
      //    and publish only after frame fields are set.
      //    Since this potentially shares the channel, other reactors can
      //    send a message and revive this reactor too early. We need to manually mark
      //    the reactor as already active to prevent this.
      frame.active = true
      frame.defaultConnector = frame.openConnector[T](
        proto.channelName, factory, false, false, immutable.Map())
      frame.internalConnector = frame.openConnector[SysEvent](
        "system", factory, true, false, immutable.Map())
      frame.internalConnector.asInstanceOf[Connector[SysEvent]].events.onEvent {
        x => frame.sysEmitter.react(x, null)
      }

      // 6. Schedule for first execution.
      frame.activate(true)
    } catch {
      case t: Throwable =>
        // 7. If not successful, release the name and rethrow.
        var done = false
        while (!done) {
          val info = frames.forName(uname)
          if (info == null) {
            // This should only happen if `activate` scheduled the frame and then threw
            // an exception, OR if `initSchedule` of a custom scheduler schedules the
            // frame for execution, which is not allowed.
            throw new IllegalStateException("Frame removed before being scheduled.")
          } else {
            val ninfo = info.retainOnlyListeners
            done = {
              if (ninfo != null) frames.tryReplace(uname, info, ninfo)
              else frames.tryRelease(uname)
            }
          }
        }
        throw t
    }

    // 8. Return the default channel.
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
  ) = {
    new ReactorSystem(name, bundle)
  }

  /** Contains machine information.
   */
  private val machineConfig = Configuration.parse(Platform.machineConfiguration)

  /** Retrieves the default bundle config object.
   *
   *  This configuration is merged with any custom configurations that are provided to
   *  the reactor system bundle.
   */
  val defaultConfig =
    Configuration.parse(Platform.defaultConfiguration).withFallback(machineConfig)

  /** Convert the configuration string to a `Configuration` object.
   */
  def customConfig(txt: String): Configuration = Configuration.parse(txt)

  /** Contains various configuration values related to the reactor system,
   *  such as the set of registered schedulers and the system url.
   */
  class Bundle(
    val defaultScheduler: Scheduler,
    private val customConfig: Configuration
  ) {
    private val schedulers = mutable.Map[String, Scheduler]()

    def this(s: Scheduler, config: String) = this(s, Configuration.parse(config))

    /** The set of configuration variables for the reactor system.
     */
    val config = customConfig.withFallback(defaultConfig)

    /** Scheduler configuration options.
     */
    object schedulerConfig {
      val defaultBudget = config.int("scheduler.default.budget")
      val postscheduleCount = config.int("scheduler.default.postschedule-count")
      val spindownInitial = config.int("scheduler.spindown.initial")
      val spindownMax = config.int("scheduler.spindown.max")
      val spindownMin = config.int("scheduler.spindown.min")
      val spindownCooldownRate = config.int("scheduler.spindown.cooldown-rate")
      val spindownMutationRate = config.double("scheduler.spindown.mutation-rate")
      val spindownTestThreshold = config.int("scheduler.spindown.test-threshold")
      val spindownTestIterations = config.int("scheduler.spindown.test-iterations")
    }

    val urlMap = config.children("remote").map { c =>
      val schema = c.string("schema")
      val url = SystemUrl(c.string("schema"), c.string("host"), c.int("port"))
      val transportName = c.string("transport")
      (schema, Bundle.TransportInfo(url, transportName))
    } toMap

    val urls = urlMap.map(_._2.url).toSet

    val pickler = {
      Platform.Reflect.instantiate(config.string("pickler"), Nil).asInstanceOf[Pickler]
    }

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
    case class TransportInfo(url: SystemUrl, transportName: String)

    /** A bundle with default schedulers from the `Scheduler` companion object.
     *  
     *  @return           the default scheduler bundle
     */
    def default(default: Scheduler): Bundle = {
      val b = new Bundle(default, Configuration.empty)
      Platform.registerDefaultSchedulers(b)
      b
    }
  }

  /** Default scheduler bundle.
   */
  def defaultBundle = Bundle.default(Platform.defaultScheduler)
}
