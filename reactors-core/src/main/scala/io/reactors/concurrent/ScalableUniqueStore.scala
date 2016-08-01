package io.reactors
package concurrent



import io.reactors.common.concurrent.UidGenerator
import java.util.concurrent.ConcurrentHashMap
import scala.collection.concurrent.TrieMap
import scala.annotation.tailrec
import scala.collection._



/** Stores `Identifiable` objects along with their unique names, in a scalable manner.
 *
 *  The UIDs of the objects stored in this data structure must always be unique for an
 *  instance of the data structure. Users may use `reserveId` to achieve this. The
 *  names of the objects need not be unique.
 */
final class ScalableUniqueStore[T >: Null <: Identifiable with AnyRef](
  val uniqueNamePrefix: String,
  val scalability: Int
) {
  private val uidCounter = new UidGenerator(scalability)
  private val byName = new TrieMap[String, T]

  /** Compute and return a unique id.
   */
  def reserveId(): Long = uidCounter.generate()

  /** Atomically returns the values in this unique store.
   */
  def values: Iterable[(Long, String, T)] = {
    for ((name, obj) <- byName.snapshot) yield (obj.uid, name, obj)
  }

  /** Attempt to store the value `x` with the `proposedName`.
   *
   *  '''Note:''' the UID of `x` must be unique among all `x` ever stored in this
   *  data structure. Use `reserveId` to obtain a UID.
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
  def tryStore(proposedName: String, x: T): String = {
    @tailrec def store(count: Long): String = {
      val suffix = if (count == 0) "" else s"-$count"
      val possibleName = s"$uniqueNamePrefix-${x.uid}$suffix"
      if (byName.putIfAbsent(possibleName, x) != None) store(count + 1)
      else possibleName
    }
    if (proposedName != null) {
      if (byName.putIfAbsent(proposedName, x) != None)
        throw new IllegalArgumentException(s"Name $proposedName unavailable.")
      proposedName
    } else {
      store(0)
    }
  }

  /** Attempts to release the specified name.
   *
   *  @param name            the name under which an object was stored
   *  @return                `true` if released, `false` otherwise
   */
  def tryRelease(name: String): Boolean = {
    byName.remove(name) match {
      case None =>
        false
      case Some(v) =>
        true
    }
  }

  /** Returns an object stored under the specified name, or `null`.
   */
  def forName(name: String): T = byName.get(name) match {
    case None => null
    case Some(x) => x
  }
}
