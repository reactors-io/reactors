package org.reactress
package isolate



import scala.collection._



/** Default isolate system implementation.
 *
 *  @param name      the name of this isolate system
 */
class DefaultIsolateSystem(val name: String) extends IsolateSystem {
  private val isolates = mutable.Map[String, String]()

  def isolate[T, I <: ReactIsolate[T, _]](proto: Proto[I])(s: Scheduler): Channel[T] = {
    ???
  }

}
