package scala.reactive
package isolate



import java.util.concurrent.atomic._
import scala.annotation.tailrec
import scala.reactive.container.RMap
import scala.reactive.util.Monitor



/** Stores `Identifiable` objects along with their unique names.
 */
final class UniqueStore[T >: Null <: Identifiable with AnyRef](
  val uniqueNamePrefix: String,
  val monitor: Monitor
) {
  private val idCounter = new AtomicLong(0L)
  private val byName = RMap[String, T]
  private val byId = RMap[Long, String]

  val nil = byName.nil

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
   *  @return                 name under which `x` was stored
   *  @throws                 an `IllegalArgumentException` if the proposed name already
   *                          exists in the map
   */
  def tryStore(proposedName: String, x: T): String = monitor.synchronized {
    @tailrec def uniqueName(count: Long): String = {
      val suffix = if (count == 0) "" else s"-$count"
      val possibleName = s"$uniqueNamePrefix-${x.uid}$suffix"
      if (byName.contains(possibleName)) uniqueName(count + 1)
      else possibleName
    }
    val uname = if (proposedName != null) proposedName else uniqueName(0)
    if (byName.contains(uname)) {
      throw new IllegalArgumentException(s"Name $proposedName unavailable.")
    } else {
      byName(uname) = x
      byId(x.uid) = uname
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

  /** Releases the object under the specified id.
   *
   *  @param id              the unique id of the object
   *  @return                `true` if released, `false` otherwise
   */
  def tryReleaseById(id: Long): Boolean = monitor.synchronized {
    val uname = byId.applyOrNil(id)
    if (uname == byId.nil) false
    else tryRelease(uname)
  }

  /** Returns an object stored under the specified name, or `null`.
   */
  def forName(name: String): T = monitor.synchronized {
    byName.applyOrNil(name)
  }

  /** Returns an object stored under the specified `id`, or `null`.
   */
  def forId(id: Long): T = monitor.synchronized {
    val uname = byId.applyOrNil(id)
    if (uname == byId.nil) byName.nil
    else byName(uname)
  }
}
