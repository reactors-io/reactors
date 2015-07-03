package scala.reactive






package object isolate {

  /** Object used for synchronization.
   */
  final class Monitor extends AnyRef {
  }

  /* isolate types */

  trait Looper[@spec(Int, Long, Double) T] extends Iso[T] {
    val fallback: Signal[Option[T]]

    def initialize() {
      import implicits.canLeak
      sysEvents onCase {
        case IsoStarted | IsoEmptyQueue => fallback() match {
          case Some(v) => later.enqueueIfEmpty(v)
          case None => channel.seal()
        }
      }
    }

    initialize()
  }

}
