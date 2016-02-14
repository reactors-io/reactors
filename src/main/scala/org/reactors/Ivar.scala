package org.reactors



import scala.collection._
import scala.reactive.util._



/** An event stream that can be either completed, or unreacted at most once.
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
extends Signal[T] with Events.Push[T] {
  private var state = 0
  private var exception: Throwable = _
  private var value: T = _

  /** Returns `true` iff the ivar has been completed, i.e. assigned or failed.
   */
  def isCompleted: Boolean = state != 0

  /** Returns `true` iff the ivar has been assigned.
   */
  def isAssigned: Boolean = state == 1

  /** Returns `true` iff the ivar has been failed with an exception.
   */
  def isFailed: Boolean = state == -1

  /** Returns `true` iff the ivar is unassigned.
   */
  def isUnassigned: Boolean = state == 0

  /** Returns the value of the ivar if it was failed already assigned,
   *  and throws an exception otherwise.
   */
  def apply(): T = {
    if (state == 1) value
    else if (state == -1) throw exception
    else sys.error("Ivar unassigned.")
  }

  def unsubscribe() = tryUnreact()

  /** Returns true if this ivar is unassigned or failed.
   */
  def isEmpty = isUnassigned || isFailed

  /** Returns the exception in the ivar if it was failed.
   *
   *  Throws an exception otherwise.
   */
  def failure: Throwable = {
    if (state == -1) exception
    else sys.error("Ivar not failed.")
  }

  /** Assigns a value to the ivar if it is unassigned,
   *  
   *  Throws an exception otherwise.
   */
  def :=(x: T): Unit = if (state == 0) {
    state = 1
    value = x
    reactAll(x)
    unreactAll()
  } else sys.error("Ivar is already assigned.")

  def react(x: T) = this := x

  def tryReact(x: T): Boolean = if (state == 0) {
    react(x)
    true
  } else false

  def except(t: Throwable) = if (!tryExcept(t)) sys.error("Ivar is already completed.")

  def tryExcept(t: Throwable): Boolean = if (state == 0) {
    state = -1
    exception = t
    exceptAll(exception)
    unreactAll()
    true
  } else false

  /** Fails the ivar with the `NoSuchElementException` if it is unassigned.
   *
   *  If completed, throws an exception.
   */
  def unreact(): Unit = tryUnreact() || sys.error("Ivar is already completed.")

  /** Tries to fail the ivar with the `NoSuchElementException`.
   *
   *  Returns `false` if the ivar is completed.
   */
  def tryUnreact(): Boolean = tryExcept(new NoSuchElementException)

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
