package org.reactress
package isolate






/** Default isolate system implementation
 *
 *  @param name      the name of this isolate system
 */
class DefaultIsolateSystem(val name: String) extends IsolateSystem {

  def isolate[T, I <: Isolate[T]](proto: Proto[I])(s: Scheduler): Channel[T] = {
    ???
  }

}
