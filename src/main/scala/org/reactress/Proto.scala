package org.reactress



import scala.reflect.ClassTag



/** A prototype for instantiating an isolate that
 *  takes specific parameters.
 * 
 *  @tparam I         type of the isolate
 */
final class Proto[I <: ReactIsolate[_, _]] private[reactress] (val clazz: Class[I], val params: Seq[Any]) {

  /** Instantiates and returns the isolate.
   */
  def create(): I = util.Reflect.instantiate(clazz, params)

}


object Proto {

  /** Creates prototype for instantiating an isolate that takes specific parameters.
   * 
   *  @tparam I         type of the isolate, must be a concrete type, or its class tag must be in scope
   *  @param params     parameters for instantiating the prototype
   *  @return           a new prototype of an isolate of type `T` with the specified parameters
   */
  def apply[I <: ReactIsolate[_, _]: ClassTag](params: Any*) = new Proto[I](implicitly[ClassTag[I]].erasure.asInstanceOf[Class[I]], params)

}

