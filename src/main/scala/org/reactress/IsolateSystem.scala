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

}


/** Contains factory methods for creating isolate systems.
 */
object IsolateSystem {

}


