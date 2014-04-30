package org.reactress



import scala.annotation.tailrec
import scala.util.DynamicVariable



/** A system used to create, track and identify isolates.
 *
 *  An isolate system is composed of a set of isolates that have
 *  a common configuration.
 */
abstract class IsolateSystem {

  /** Name of this isolate system.
   *
   *  @return          the name of the isolate system
   */
  def name: String

  /** Creates an isolate in this isolate system using the specified scheduler.
   *
   *  @tparam T         the type of the events for the isolate
   *  @tparam I         the type of the isolate
   *  @param proto      the prototype for the isolate
   *  @param scheduler  the scheduler used to scheduler the isolate
   *  @return           the channel for this isolate
   */
  def isolate[T, I <: ReactIsolate[T, _]](proto: Proto[I])(s: Scheduler): Channel[T]

}


/** Contains factory methods for creating isolate systems.
 */
object IsolateSystem {

}


