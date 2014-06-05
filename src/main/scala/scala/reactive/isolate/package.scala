package scala.reactive






package object isolate {

  trait Looper[@spec(Int, Long, Double) T] extends Isolate[T] {
    val fallback: Signal[Option[T]]

    def initialize() {
      react <<= sysEvents onCase {
        case IsolateStarted | IsolateEmptyQueue => fallback() match {
          case Some(v) => later.enqueueIfEmpty(v)
          case None => channel.seal()
        }
      }
    }

    initialize()
  }

}
