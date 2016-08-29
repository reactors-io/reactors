package io.reactors



import io.reactors.protocols._
import org.coroutines._
import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context



package object direct {
  def reactorCoroutine[T: c.WeakTypeTag](c: Context)(body: c.Tree): c.Tree = {
    import c.universe._
    val coroutineName = TermName(c.freshName("c"))
    q"""
      val $coroutineName = _root_.org.coroutines.coroutine($body)
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
    /** Suspends execution of the reactor until the event stream produces an event.
     *
     *  This combinator can only be used inside suspendable reactors. The coroutine it
     *  returns is meant to be invoked from another coroutine or a suspendable context,
     *  but not started with the `call` combinator.
     *
     *  Example:
     *
     *  {{{
     *  system.spawn(Reactor.suspendable { (self: Reactor[String]) =>
     *    val x = self.main.events.receive()
     *    println(x)
     *  })
     *  }}}
     *
     *  @return    A coroutine that yields an `onEvent` function, and returns the event.
     */
    def receive: ~~~>[(() => Unit) => Subscription, T] = {
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

  def backpressureSend[T: c.WeakTypeTag](c: Context)(body: c.Tree): c.Tree = {
    import c.universe._
    q"""
      ???
    """
  }

  implicit class BackpressureLinkOps[T](val link: Backpressure.Link[T]) {
    def !(x: T): Unit = ???
  }
}
