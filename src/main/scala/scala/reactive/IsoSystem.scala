package scala.reactive



import com.typesafe.config._
import java.util.concurrent.atomic._
import scala.annotation.tailrec
import scala.collection._
import scala.reactive.isolate._
import scala.reactive.util.Monitor



/** A system used to create, track and identify isolates.
 *
 *  An isolate system is composed of a set of isolates that have
 *  a common configuration.
 */
abstract class IsoSystem extends isolate.Services {

  /** Encapsulates the internal state of the isolate system.
   */
  private[reactive] class State {
    val monitor = new Monitor
    val frames = new UniqueStore[Frame]("isolate", monitor)
  }

  private[reactive] val state = new State

  /** Retrieves the bundle for this isolate system.
   *  
   *  @return           the bundle
   */
  def bundle: IsoSystem.Bundle

  /** Name of this isolate system instance.
   *
   *  @return          the name of the isolate system
   */
  def name: String

  /** Retrieves the register of channels in this isolate system.
   *  
   *  @return          the channels register
   */
  def channels: IsoSystem.Channels

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
  def isolate[@spec(Int, Long, Double) T: Arrayable](p: Proto[Iso[T]]): Chan[T] = {
    tryCreateIsolate(p)
  }

  protected[reactive] def tryCreateIsolate[@spec(Int, Long, Double) T: Arrayable](
    proto: Proto[Iso[T]]
  ): Chan[T] = {
    // 1. ensure a unique id
    val uid = state.frames.reserveId()
    val scheduler = Scheduler2.newThread
    val factory = EventQ.UnrolledRing.Factory
    val frame = new Frame(uid, proto, scheduler, this)

    // 2. reserve the unique name or break
    val uname = state.frames.tryStore(proto.name, frame)

    try {
      // 3. allocate the standard connectors
      frame.name = uname
      frame.defaultConnector = frame.openConnector("default", factory, false)
      frame.systemConnector = frame.openConnector("system", factory, true)

      // 4. schedule for the first execution
      scheduler.startSchedule(frame)
      frame.scheduleForExecution()
    } catch {
      case t: Throwable =>
        // 5. if not successful, release the name and rethrow
        state.frames.tryRelease(uname)
        throw t
    }

    // 6. return the default channel
    frame.defaultConnector.channel.asInstanceOf[Chan[T]]
  }

}


/** Contains factory methods for creating isolate systems.
 */
object IsoSystem {

  /** Creates the default isolate system.
   *  
   *  @param name       the name for the isolate system instance
   *  @param scheduler  the default scheduler
   *  @return           a new isolate system instance
   */
  def default(name: String, bundle: IsoSystem.Bundle = IsoSystem.defaultBundle) =
    new isolate.DefaultIsoSystem(name, bundle)

  /** Retrieves the default bundle config object.
   *
   *  This configuration is merged with any custom configurations that are provided to
   *  the isolate system bundle.
   */
  val defaultConfig: Config = {
    ConfigFactory.parseString("""
      system = {
        net = {
          parallelism = 4
        }
      }
    """)
  }

  /** Contains a set of schedulers registered with each isolate system.
   */
  class Bundle(val defaultScheduler: Scheduler2, private val customConfig: Config) {
    private val schedulers = mutable.Map[String, Scheduler2]()

    /** The set of configuration variables for the isolate system.
     */
    val config = customConfig.withFallback(defaultConfig)

    /** Retrieves the scheduler registered under the specified name.
     *  
     *  @param name        the name of the scheduler
     *  @return            the scheduler object associated with the name
     */
    def scheduler(name: String): Scheduler2 = {
      schedulers(name)
    }
  
    /** Does an inverse lookup for the name of this scheduler instance.
     *  The method fails if this specific scheduler instance was not previously
     *  registered with the isolate system.
     *
     *  @param scheduler           scheduler that was previously registered
     *  @return                    name of the previously registered scheduler
     */
    def schedulerName(s: Scheduler2): String = {
      schedulers.find(_._2 eq s).get._1
    }

    /** Registers the scheduler under a specific name,
     *  so that it can be later retrieved using the 
     *  `scheduler` method.
     *
     *  @param name       the name under which to register the scheduler
     *  @param s          the scheduler object to register
     */
    def registerScheduler(name: String, s: Scheduler2) {
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
    def default(default: Scheduler2): Bundle = {
      val b = new Bundle(default, ConfigFactory.empty)
      b.registerScheduler(schedulers.globalExecutionContext,
        Scheduler2.globalExecutionContext)
      b.registerScheduler(schedulers.default, Scheduler2.default)
      b.registerScheduler(schedulers.newThread, Scheduler2.newThread)
      b.registerScheduler(schedulers.piggyback, Scheduler2.piggyback)
      b
    }
  }

  /** Default scheduler bundle.
   */
  lazy val defaultBundle = Bundle.default(Scheduler2.default)

  class ChannelBuilder(
    val channelName: String,
    val isDaemon: Boolean,
    val eventQueueFactory: EventQ.Factory
  ) {
    /** Associates a new name for the channel.
     */
    def named(name: String) =
      new ChannelBuilder(name, isDaemon, eventQueueFactory)

    /** Specifies a daemon channel.
     */
    def daemon = new ChannelBuilder(channelName, true, eventQueueFactory)

    /** Associates a new event queue factory.
     */
    def eventQueue(factory: EventQ.Factory) =
      new ChannelBuilder(channelName, isDaemon, factory)

    /** Opens a new channel for this isolate.
     *
     *  @tparam Q        type of the events in the new channel
     *  @return          the connector object of the new channel
     */
    final def open[@spec(Int, Long, Double) Q: Arrayable]: Conn[Q] =
      Iso.self.frame.openConnector[Q](channelName, eventQueueFactory, isDaemon)
  }

  /** The channel register used for channel lookup by name.
   */
  class Channels extends ChannelBuilder(null, false, null)

}


