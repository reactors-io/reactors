package scala.reactive






package object isolate {

  /* isolate types */

  trait Looper[@spec(Int, Long, Double) T] extends Iso[T] {
    val fallback: Signal[Option[T]]

    def initialize() {
      react <<= sysEvents onCase {
        case IsoStarted | IsoEmptyQueue => fallback() match {
          case Some(v) => later.enqueueIfEmpty(v)
          case None => channel.seal()
        }
      }
    }

    initialize()
  }

  /* event types */

}
