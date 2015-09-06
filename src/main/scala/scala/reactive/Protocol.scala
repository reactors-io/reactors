package scala.reactive



import scala.reflect.ClassTag



/** An encapsulation of a set of event streams and channels.
 */
trait Protocol {
  def system: IsoSystem
}
