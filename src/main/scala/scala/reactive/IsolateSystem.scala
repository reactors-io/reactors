package scala.reactive



import scala.annotation.tailrec
import scala.util.DynamicVariable



/** A system used to create, track and identify isolates.
 *
 *  An isolate system is composed of a set of isolates that have
 *  a common configuration.
 */
abstract class IsolateSystem {

  /** Name of this isolate system instance.
   *
   *  @return          the name of the isolate system
   */
  def name: String

  /** Creates an isolate in this isolate system using the specified scheduler.
   *
   *  '''Use case:'''
   *  {{{
   *  def isolate(proto: Proto[Isolate[T]]): Channel[T]
   *  }}}
   *
   *  @tparam T         the type of the events for the isolate
   *  @tparam Q         the type of the events in the event queue of the isolate,
   *                    for most isolate types the same as `T`
   *  @param proto      the prototype for the isolate
   *  @param scheduler  the scheduler used to scheduler the isolate
   *  @return           the channel for this isolate
   */
  def isolate[@spec(Int, Long, Double) T: Arrayable](proto: Proto[Isolate[T]], name: String = null): Channel[T]

  /** Generates a unique isolate name.
   *
   *  @return           a unique isolate name
   */
  protected def uniqueName(): String

  /** Retrieves the scheduler bundle for this isolate system.
   *  
   *  @return           the scheduler bundle
   */
  def bundle: Scheduler.Bundle

}


/** Contains factory methods for creating isolate systems.
 */
object IsolateSystem {

  /** Retrieves the default isolate system.
   *  
   *  @param name       the name for the isolate system instance
   *  @param scheduler  the default scheduler
   *  @return           a new isolate system instance
   */
  def default(name: String, bundle: Scheduler.Bundle = Scheduler.defaultBundle) = new isolate.DefaultIsolateSystem(name, bundle)

}


