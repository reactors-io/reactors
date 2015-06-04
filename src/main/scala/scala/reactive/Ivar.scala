package scala.reactive



import scala.collection._
import scala.reactive.util._



/** An event stream that can be either completed or unreacted at most once.
 *
 *  Assigning a value propagates an event to all the subscribers,
 *  and then propagates an unreaction.
 *  To assign a value:
 *
 *  {{{
 *  val iv = new Events.Ivar[Int]
 *  iv := 5
 *  assert(iv() == 5)
 *  }}}
 *
 *  To unreact (i.e. seal, or close) an ivar without assigning a value:
 *
 *  {{{
 *  val iv = new Events.Ivar[Int]
 *  iv.unreact()
 *  assert(iv.isUnreacted)
 *  }}}
 *  
 *  @tparam T          type of the value in the `Ivar`
 */
class Ivar[@spec(Int, Long, Double) T]
extends Events[T] with Events.Default[T] with EventSource {
  private var subscription = Events.Subscription.empty
  private var state = 0
  private var value: T = _

  /** Returns `true` iff the ivar has been assigned.
   */
  def isAssigned: Boolean = state == 1

  /** Returns `true` iff the ivar has been closed.
   */
  def isUnreacted: Boolean = state == -1

  /** Returns `true` iff the ivar is unassigned.
   */
  def isUnassigned: Boolean = state == 0

  /** Returns the value of the ivar if it has been already assigned,
   *  and throws an exception otherwise.
   */
  def apply(): T = {
    if (state == 1) value
    else if (state == -1) sys.error("Ivar closed.")
    else sys.error("Ivar unassigned.")
  }

  /** Assigns a value to the ivar if it is unassigned,
   *  and throws an exception otherwise.
   */
  def :=(x: T): Unit = if (state == 0) {
    state = 1
    value = x
    reactAll(x)
    unreactAll()
  } else sys.error("Ivar is not unassigned.")

  /** Invokes the callback immediately if the ivar is assigned,
   *  or executes it once the ivar is assigned.
   *
   *  If the ivar gets closed, the callback is never invoked.
   *
   *  @param f        the callback to invoke with the ivar value
   */
  def use(f: T => Unit): Events.Subscription = {
    if (isAssigned) {
      f(this())
      Events.Subscription.empty
    } else foreach(f)
  }

  /** Closes the ivar iff it is unassigned.
   */
  def unreact(): Unit = if (state == 0) {
    state = -1
    unreactAll()
  }

  /** Returns an `Ivar` that is completed with the value of this `Ivar`
   *  if this `Ivar` is assigned.
   *  If this `Ivar` unreacts, the returned `Ivar` is instead completed with `v`.
   *  
   *  Note that, unless this `Ivar` gets assigned or unreacts,
   *  the resulting `Ivar` will not be assigned any values.
   */
  def orElse(v: =>T): Ivar[T] = {
    if (this.isUnreacted) Ivar(v)
    else if (this.isAssigned) Ivar(this())
    else {
      val iv = new Ivar[T]
      iv.subscription = this.observe(new Reactor[T] {
        def react(x: T) {
          iv := x
          iv.subscription.unsubscribe()
          iv.subscription = Events.Subscription.empty
        }
        def except(t: Throwable) {}
        def unreact() {
          iv := v
          iv.subscription.unsubscribe()
          iv.subscription = Events.Subscription.empty
        }
      })
      iv
    }
  }

  /** Returns an `Ivar` that is completed with the value of this `Ivar`
   *  if this `Ivar` is assigned.
   *  If this `Ivar` unreacts, the returned `Ivar` is instead completed with
   *  the first event from the specified event stream `r`.
   *
   *  Note that, if this `Ivar` unreacts at time T, only the events from `r` after the
   *  time T are considered for assignment to the resulting `Ivar`.
   */
  def orElseWith(r: Events[T]): Ivar[T] = {
    if (this.isUnreacted) r.ivar
    else if (this.isAssigned) Ivar(this())
    else {
      val iv = new Ivar[T]
      iv.subscription = this.observe(new Reactor[T] {
        def react(x: T) {
          iv := x
          iv.subscription.unsubscribe()
          iv.subscription = Events.Subscription.empty
        }
        def except(t: Throwable) {}
        def unreact() {
          iv.subscription = r.foreach { x =>
            iv := x
            iv.subscription.unsubscribe()
            iv.subscription = Events.Subscription.empty
          }
        }
      })
      iv
    }
  }
}


object Ivar {
  def apply[@spec(Int, Long, Double) T](x: T): Ivar[T] = {
    val iv = new Ivar[T]
    iv := x
    iv
  }

  def unreacted[@spec(Int, Long, Double) T]: Ivar[T] = {
    val iv = new Ivar[T]
    iv.unreact()
    iv
  }
}
