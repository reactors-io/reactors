package org.reactress



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
  def isolate[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q: Arrayable](proto: Proto[ReactIsolate[T, Q]], name: String = null)(implicit scheduler: Scheduler): Channel[T]

  /** Generates a unique isolate name.
   *
   *  @return           a unique isolate name
   */
  protected def uniqueName(): String

}


/** Contains factory methods for creating isolate systems.
 */
object IsolateSystem {

  def default(name: String) = new isolate.DefaultIsolateSystem(name)

}


