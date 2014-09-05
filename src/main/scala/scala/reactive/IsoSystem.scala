package scala.reactive



import java.util.concurrent.atomic._
import scala.annotation.tailrec
import scala.collection._
import scala.util.DynamicVariable
import scala.reactive.isolate._



/** A system used to create, track and identify isolates.
 *
 *  An isolate system is composed of a set of isolates that have
 *  a common configuration.
 */
abstract class IsoSystem {

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
   *  Implementations of this method must initialize the isolate frame with the `createFrame` method,
   *  add the isolate to the specific bookkeeping code,
   *  and then call the `wake` method on the isolate frame to start it for the first time.
   *  Finally, they must return the isolate's default channel.
   *
   *  @tparam T         the type of the events for the isolate
   *  @tparam Q         the type of the events in the event queue of the isolate,
   *                    for most isolate types the same as `T`
   *  @param proto      the prototype for the isolate
   *  @param scheduler  the scheduler used to scheduler the isolate
   *  @return           the channel for this isolate
   */
  def isolate[@spec(Int, Long, Double) T: Arrayable](proto: Proto[Iso[T]], name: String = null): Channel[T]

  /** Creates a new channel for the specified isolate frame.
   *
   *  '''Note:'''
   *  The `channel` field of the isolate frame is not set at the time this method is called.
   *  
   *  @tparam Q         the type of the events for the isolate
   *  @param frame      the isolate frame for the channel
   *  @return           the new channel for the isolate frame
   */
  protected[reactive] def newChannel[@spec(Int, Long, Double) Q](reactor: Reactor[Q]): Channel[Q]

  /** Generates a unique isolate name if the `name` argument is `null`,
   *  and throws an exception if the `name` is already taken.
   *
   *  The implementation of this method needs to be thread-safe.
   *
   *  @param name       proposed name
   *  @return           a unique isolate name
   */
  protected def uniqueName(name: String): String

  /** Releases the name after the isolate terminates.
   *  
   *  @param name       the name to release
   */
  protected[reactive] def releaseName(name: String): Unit

  /** Generates a new unique id, generated only once during
   *  the lifetime of this isolate system.
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

  /** Creates an isolate frame.
   *
   *  Should only be overridden if the default isolate initialization order needs to change.
   *  See the source code of the default implementation of this method for more details.
   *
   *  @tparam T         the type of the events for the isolate
   *  @param proto      prototype for the isolate
   *  @param name       name of the new isolate
   *  @return           the resulting isolate frame
   */
  protected def createFrame[@spec(Int, Long, Double) T: Arrayable](proto: Proto[Iso[T]], name: String): Iso[T] = {
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
    val uname = uniqueName(name)
    val frame = new IsoFrame(
      uid,
      uname,
      IsoSystem.this,
      scheduler,
      queueFactory,
      multiplexer,
      frame => Iso.openChannel(frame, queueFactory)
    )
    val isolate = Iso.argFrame.withValue(frame) {
      createAndResetIso(proto)
    }
    frame.isolate = isolate
    scheduler.initiate(frame)
    isolate
  }

}


/** Contains factory methods for creating isolate systems.
 */
object IsoSystem {

  /** Retrieves the default isolate system.
   *  
   *  @param name       the name for the isolate system instance
   *  @param scheduler  the default scheduler
   *  @return           a new isolate system instance
   */
  def default(name: String, bundle: IsoSystem.Bundle = IsoSystem.defaultBundle) = new isolate.DefaultIsoSystem(name, bundle)

  /** Contains a set of schedulers registered with each isolate system.
   */
  class Bundle(val defaultScheduler: Scheduler) {
    private val schedulers = mutable.Map[String, Scheduler]()

    /** Retrieves the scheduler registered under the specified name.
     *  
     *  @param name        the name of the scheduler
     *  @return            the scheduler object associated with the name
     */
    def scheduler(name: String): Scheduler = {
      schedulers(name)
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
    /** A bundle with default schedulers from the `Scheduler` companion object.
     *  
     *  @return           the default scheduler bundle
     */
    def default(default: Scheduler): Bundle = {
      val b = new Bundle(default)
      b.registerScheduler("scala.reactive.Scheduler.globalExecutionContext", Scheduler.globalExecutionContext)
      b.registerScheduler("scala.reactive.Scheduler.default", Scheduler.default)
      b.registerScheduler("scala.reactive.Scheduler.newThread", Scheduler.newThread)
      b.registerScheduler("scala.reactive.Scheduler.piggyback", Scheduler.piggyback)
      b
    }
  }

  /** The channel register used for channel lookup by name.
   */
  trait Channels {
    /** Registers a new channel with this isolate system.
     *
     *  Throws an exception if name is already taken.
     *  
     *  @param name       name of the channel
     *  @param channel    the channel to register
     */
    def update(name: String, channel: Channel[_]): Unit

    /** Returns a channel under the specified name, if any.
     *
     *  Throws an exception if such a channel does not exist.
     *  
     *  @param name       name of the channel
     *  @return           the channel registered under the specified name
     */
    def apply[@spec(Int, Long, Double) T](name: String): Channel[T]

    /** Eventually returns a channel under the specified name.
     *
     *  @param name       name of the channel
     *  @return           the ivar with the channel registered under the specified name
     */
    def get[@spec(Int, Long, Double) T](name: String): Reactive.Ivar[Channel[T]]

    /** Eventually returns an *unsealed* channel under the specified name.
     *
     *  @param name       name of the channel
     *  @return           the ivar with the channel registered under the specified name
     */
    def getUnsealed[@spec(Int, Long, Double) T](name: String): Reactive.Ivar[Channel[T]]
  }

  /** Default scheduler bundle.
   */
  lazy val defaultBundle = Bundle.default(Scheduler.default)

}


