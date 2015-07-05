package scala.reactive



import scala.reflect.ClassTag



/** A prototype for instantiating an isolate that
 *  takes specific parameters.
 * 
 *  @tparam I         type of the isolate
 */
final class Proto[+I <: Iso[_]] private[reactive] (
  val clazz: Class[_],
  val params: Seq[Any],
  val scheduler: String = null,
  val eventQueueFactory: EventQ.Factory = null,
  val name: String = null
) {

  /** Instantiates and returns the isolate.
   */
  def create(): I = util.Reflect.instantiate(clazz, params).asInstanceOf[I]

  /** Associates the specified scheduler and returns the new `Proto` object.
   *
   *  Note that the scheduler name needs to be registered with the `IsoSystem` object.
   *  
   *  @param sname               name of the scheduler
   *  @return                    a new `Proto` object
   */
  def withScheduler(sname: String): Proto[I] =
    new Proto(clazz, params, sname, eventQueueFactory, name)

  /** Associates the specified event queue type and returns the new `Proto` object.
   *  
   *  @param f                   event queue factory, used to instantiate the event
   *                             queue object
   *  @return                    a new `Proto` object
   */
  def withEventQueue(f: EventQ.Factory): Proto[I] =
    new Proto(clazz, params, scheduler, f, name)

  /** Associates the name for the new isolate and returns the new `Proto` object.
   */
  def withName(nm: String): Proto[I] =
    new Proto(clazz, params, scheduler, eventQueueFactory, nm)

}


object Proto {

  /** Creates prototype for instantiating an isolate that takes no parameters.
   * 
   *  @tparam I         type of the isolate, must be a concrete type, or its class tag
   *                    must be in scope
   *  @return           a new prototype of an isolate of type `T`
   */
  def apply[I <: Iso[_]: ClassTag] =
    new Proto[I](implicitly[ClassTag[I]].erasure.asInstanceOf[Class[I]], Seq())

  /** Creates prototype for instantiating an isolate that takes specific parameters.
   * 
   *  @tparam I         type of the isolate, must be a concrete type, or its class tag
   *                    must be in scope
   *  @param clazz      class that describes the isolate
   *  @param params     parameters for instantiating the prototype
   *  @return           a new prototype of an isolate of type `T` with the specified
   *                    parameters
   */
  def apply[I <: Iso[_]: ClassTag](params: Any*) =
    new Proto[I](implicitly[ClassTag[I]].erasure.asInstanceOf[Class[I]], params)

}

