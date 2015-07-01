package scala.reactive



import com.typesafe.config._
import java.util.concurrent.atomic._
import scala.annotation.tailrec
import scala.collection._
import scala.reactive.isolate._



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
    val frames = new NameMap[Frame]("isolate", monitor)
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
   *  @tparam Q         the type of the events in the event queue of the isolate,
   *                    for most isolate types the same as `T`
   *  @param proto      the prototype for the isolate
   *  @param scheduler  the scheduler used to scheduler the isolate
   *  @return           the channel for this isolate
   */
  def isolate[@spec(Int, Long, Double) T: Arrayable](proto: Proto[Iso[T]]): Channel[T]

  /** Creates a new channel for the specified isolate frame.
   *
   *  '''Note:'''
   *  The `channel` field of the isolate frame is not set at the time this method is
   *  called.
   *  
   *  @tparam Q         the type of the events for the isolate
   *  @param frame      the isolate frame for the channel
   *  @return           the new channel for the isolate frame
   */
  protected[reactive] def newChannel[@spec(Int, Long, Double) Q](reactor: Reactor[Q]):
    Channel[Q]

  /** Generates a unique isolate name if the `name` argument is `null`,
   *  and throws an exception if the `name` is already taken.
   *
   *  The implementation of this method needs to be thread-safe.
   *
   *  @param name       proposed name
   *  @return           a unique isolate name
   */
  protected def uniqueName(name: String): String

  /** Releases the channel names associated with the isolate,
   *  and then releases the name of the isolate.
   *
   *  Called after the isolate terminates.
   *  
   *  @param name       the name to release
   */
  protected[reactive] def releaseNames(name: String): Unit

  /** Generates a new unique id, generated only once during the lifetime of this
   *  isolate system.
   *
   *  The implementation of this method must be thread-safe.
   *
   *  @return           a unique id
   */
  protected def uniqueId(): Long

  /** Creates an isolate from the `Proto` object.
   *
   *  Starts by memoizing the old isolate object,
   *  and then calling the creation method.
   */
  protected def createAndResetIso[T](proto: Proto[Iso[T]]): Iso[T] = {
    val oldi = Iso.selfIso.get
    try {
      proto.create()
    } finally {
      Iso.selfIso.set(oldi)
    }
  }

  protected[reactive] def tryCreateIsolate[@spec(Int, Long, Double) T: Arrayable](
    proto: Proto[Iso[T]]
  ): Channel[T] = {
    // 1. ensure a unique id
    val uid = state.frames.reserveId()
    val scheduler = proto.scheduler match {
      case null => bundle.defaultScheduler
      case name => bundle.scheduler(name)
    }
    val frame = new Frame(uid, scheduler, this)

    // 2. reserve the unique name or break
    val uname = state.frames.tryStore(proto.name, frame)
    if (uname == null)
      throw new IllegalArgumentException(s"Isolate name ${proto.name} unavailable.")

    try {
      // 3. allocate the standard connectors
      frame.name = uname
      frame.defaultConnector = null
      frame.systemConnector = null

      // 4. schedule for the first execution
    } catch {
      case t: Throwable =>
        // 5. if not successful, release the name and rethrow
        state.frames.tryRelease(uname)
        throw t
    }

    null
  }

  /** Creates an isolate frame.
   *
   *  Should only be overridden if the default isolate initialization order needs to
   *  change.
   *  
   *  - The multiplexer, unique name and unique id are created for an isolate first.
   *  - Then, the isolate frame (`IsoFrame`) is created.
   *  - Then, the isolate object (concrete user implementation) is instantiated.
   *    - The base `Iso` constructor sets the remaining fields on the `IsoFrame` (e.g.
   *      the `isolate` field, since `Iso` object exists at this point)
   *    - The base `Iso` constructor creates (and publishes) the default channels.
   *    - The user-defined `Iso` subclass constructor is invoked.
   *  
   *  See the source code of the default implementation of this method for more details.
   *
   *  Note that the `createFrame` caller (i.e. then `isolate` method of an iso-system
   *  implementation) must:
   *  - first, update its state
   *  - then, call `frame.scheduler.initiate(frame)`
   *  - finally, call `frame.wake()`
   *
   *  @tparam T         the type of the events for the isolate
   *  @param proto      prototype for the isolate
   *  @return           the resulting isolate frame
   */
  protected def createFrame[@spec(Int, Long, Double) T: Arrayable](
    proto: Proto[Iso[T]]
  ): Iso[T] = {
    val scheduler = proto.scheduler match {
      case null => bundle.defaultScheduler
      case name => bundle.scheduler(name)
    }
    val queueFactory = proto.eventQueueFactory match {
      case null => EventQueue.SingleSubscriberSyncedUnrolledRing.factory
      case fact => fact
    }
    val multiplexer = proto.multiplexer match {
      case null => new Multiplexer.Default
      case mult => mult
    }
    val uid = uniqueId()
    val uname = uniqueName(proto.name)
    val frame = new IsoFrame(
      uid,
      uname,
      IsoSystem.this,
      scheduler,
      queueFactory,
      multiplexer,
      frame => IsoSystem.openChannel(IsoSystem.this, frame, queueFactory, "events",
        false),
      frame => IsoSystem.openChannel(IsoSystem.this, frame, queueFactory, "internal",
        true)
    )
    val isolate = Iso.argFrame.withValue(frame) {
      createAndResetIso(proto)
    }
    isolate
  }

}


/** Contains factory methods for creating isolate systems.
 */
object IsoSystem {

  /** Opens a channel for the current isolate, using the specified parameters.
   */
  @deprecated
  private[reactive] def openChannel[@spec(Int, Long, Double) Q: Arrayable](
    system: IsoSystem,
    frame: IsoFrame,
    f: EventQueue.Factory,
    cn: String,
    isDaemon: Boolean
  ): Connector[Q] = {
    val factory = if (f != null) f else Iso.self.frame.eventQueueFactory
    val channelName =
      if (cn != null) cn else "channel-" + frame.counter.incrementAndGet().toString
    val eventQueue = factory.create[Q]
    val name = frame.name + "#" + channelName
    val connector = new Connector(frame, eventQueue, name, isDaemon)
    system.channels(name) = connector.channel
    frame.multiplexer += connector
    connector
  }

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
  class Bundle(val defaultScheduler: Scheduler, private val customConfig: Config) {
    private val schedulers = mutable.Map[String, Scheduler]()

    /** The set of configuration variables for the isolate system.
     */
    val config = customConfig.withFallback(defaultConfig)

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
    val system: IsoSystem,
    val channelName: String,
    val isDaemon: Boolean,
    val eventQueueFactory: EventQueue.Factory
  ) {
    /** Associates a new name for the channel.
     */
    def named(name: String) =
      new ChannelBuilder(system, name, isDaemon, eventQueueFactory)

    /** Specifies a daemon channel.
     */
    def daemon = new ChannelBuilder(system, channelName, true, eventQueueFactory)

    /** Associates a new event queue factory.
     */
    def eventQueue(factory: EventQueue.Factory) =
      new ChannelBuilder(system, channelName, isDaemon, factory)

    /** Opens a new channel for this isolate.
     *
     *  @tparam Q        type of the events in the new channel
     *  @param factory   event queue factory
     *  @return          the connector object of the new channel
     */
    final def open[@spec(Int, Long, Double) Q: Arrayable]: Connector[Q] =
      IsoSystem.openChannel[Q](system, Iso.self.frame, eventQueueFactory, channelName,
        isDaemon)
  }

  /** The channel register used for channel lookup by name.
   */
  abstract class Channels(system: IsoSystem)
  extends ChannelBuilder(system, null, false, null) {

    /** Registers a new channel with this isolate system.
     *
     *  Throws an exception if name is already taken.
     *  
     *  @param name       name of the channel
     *  @param channel    the channel to register
     */
    private[reactive] def update(name: String, channel: Channel[_]): Unit

    /** Removes the channel registration.
     *
     *  @param name       name of the channel to remove from the register
     */
    private[reactive] def remove(name: String): Unit

    /** Removes all the channels registered with a specific isolate.
     *
     *  @param isoName    name of the isolate whose channels must be removed
     */
    private[reactive] def removeIsolate(isoName: String): Unit

    /** Eventually returns a channel under the specified name.
     *
     *  @param name       name of the channel
     *  @return           the ivar with the channel registered under the specified name
     */
    def iget[@spec(Int, Long, Double) T](name: String): Ivar[Channel[T]]

    /** Eventually returns an *unsealed* channel under the specified name.
     *
     *  Note: between the time that the channel is retrieved and the time it is first
     *  used, that channel can asynchronously become sealed.
     *  
     *  @param name       name of the channel
     *  @return           the ivar with the channel registered under the specified name
     */
    def iunsealed[@spec(Int, Long, Double) T](name: String): Ivar[Channel[T]]
  }

  object Channels {

    /** A default implementation of the channels register.
     */
    class Default(system: IsoSystem) extends IsoSystem.Channels(system) {
      private val channelMap = container.RMap[String, Channel[_]]
      private[reactive] def update(name: String, c: Channel[_]) =
        channelMap.synchronized {
          if (!channelMap.contains(name)) channelMap(name) = c
          else sys.error(s"Name $name already contained in channels.")
        }
      private[reactive] def remove(name: String): Unit = channelMap.synchronized {
        channelMap.remove(name)
      }
      private[reactive] def removeIsolate(isoName: String): Unit =
        channelMap.synchronized {
          val prefix = isoName + "#"
          val obsoleteNames = channelMap.keys.filter(_ startsWith prefix)
          for (name <- obsoleteNames) channelMap.remove(name)
        }
      private def channelExtractor[T](reqId: Long):
        PartialFunction[InternalEvent, Channel[T]] = {
        case ChannelRetrieved(`reqId`, c: Channel[T]) => c
      }
      private def getIvar[@spec(Int, Long, Double) T]
        (name: String, pred: Channel[_] => Boolean): Ivar[Channel[T]] =
        channelMap.synchronized {
        val c = channelMap.applyOrNil(name)
        if (pred(c)) Ivar(c.asInstanceOf[Channel[T]])
        else {
          val reqId = Iso.self.frame.counter.incrementAndGet()
          val sysChannel = Iso.self.sysChannel
          val desiredChannels = channelMap.react(name).filter(pred).endure
          desiredChannels.effect(_ => desiredChannels.unsubscribe())
            .map(ChannelRetrieved(reqId, _): InternalEvent).pipe(sysChannel)
          Iso.self.internalEvents.collect(channelExtractor[T](reqId)).ivar
        }
      }
      def iget[@spec(Int, Long, Double) T](name: String) = getIvar(name, c => c != null)
      def iunsealed[@spec(Int, Long, Double) T](name: String) =
        getIvar(name, c => c != null && !c.isSealed)
    }

  }

}


