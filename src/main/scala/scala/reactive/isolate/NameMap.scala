package scala.reactive
package isolate



import java.util.concurrent.atomic._
import scala.annotation.tailrec
import scala.reactive.container.RMap



/** Stores `Identifiable` objects along with their unique names.
 */
final class NameMap[T >: Null <: Identifiable with AnyRef](
  val uniqueNamePrefix: String
) {
  private val monitor = new Monitor
  private val idCounter = new AtomicLong(0L)
  private val byName = RMap[String, T]
  private val byId = RMap[Long, T]

  /** Compute and return a unique id.
   */
  def reserveId(): Long = idCounter.getAndIncrement()

  /** Attempt to store the value `x` with the `proposedName`.
   *
   *  Returns the name under which `x` is stored. If the name is not available, returns
   *  `null` and does not store the object.
   *
   *  @param proposedName     the proposed name, or `null` to assign any name
   *  @param x                the object
   *  @return                 name under which `x` was stored, or `null` if it was not
   */
  def tryStore(proposedName: String, x: T): String = monitor.synchronized {
    @tailrec def uniqueName(count: Long): String = {
      val suffix = if (count == 0) "" else s"-$count"
      val possibleName = s"$uniqueNamePrefix-${x.uid}$suffix"
      if (byName.contains(possibleName)) uniqueName(count + 1)
      else possibleName
    }
    val uname = if (proposedName != null) proposedName else uniqueName(0)
    if (byName.contains(uname)) null
    else {
      byName(uname) = x
      byId(x.uid) = x
      uname
    }
  }

  /** Attempts to release the specified name.
   *
   *  @param name            the name under which an object was stored
   *  @return                `true` if released, `false` otherwise
   */
  def tryRelease(name: String): Boolean = monitor.synchronized {
    if (!byName.contains(name)) false
    else {
      val frame = byName(name)
      byName.remove(name)
      byId.remove(frame.uid)
      true
    }
  }

  /** Returns an object stored under the specified name, or `null`.
   */
  def forName(name: String): T = monitor.synchronized {
    if (byName.contains(name)) byName(name)
    else null
  }
}
