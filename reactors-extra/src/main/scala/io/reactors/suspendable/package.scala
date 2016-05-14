package io.reactors



import org.coroutines._
import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context



package object suspendable {
  def reactorCoroutine[T: c.WeakTypeTag](c: Context)(body: c.Tree): c.Tree = {
    import c.universe._
    val coroutineName = TermName(c.freshName("c"))
    q"""
      val $coroutineName = coroutine($body)
      _root_.io.reactors.Reactor.fromCoroutine($coroutineName)
    """
  }

  implicit class ReactorCoroutineOps(val r: Reactor.type) extends AnyVal {
    def suspendable[T](body: Reactor[T] => Unit): Proto[Reactor[T]] =
      macro reactorCoroutine[T]
    def fromCoroutine[@spec(Int, Long, Double) T, R](
      c: Reactor[T] ~~> ((() => Unit) => Subscription, R)
    ): Proto[Reactor[T]] = {
      Reactor[T] { self =>
        val frame = call(c(self))
        def loop() {
          if (frame.pull) {
            val onEvent = frame.value
            onEvent(loop)
          }
        }
        loop()
      }
    }
  }

  implicit class EventsCoroutineOps[T <: AnyRef](val events: Events[T]) {
    def receive = {
      var result = null.asInstanceOf[T]
      coroutine { () =>
        val onEvent = (observer: () => Unit) => {
          events.once.onEvent { x =>
            result = x
            observer()
          }
        }
        yieldval(onEvent)
        result
      }
    }
  }
}
