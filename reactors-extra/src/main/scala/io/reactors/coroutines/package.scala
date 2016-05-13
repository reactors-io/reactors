package io.reactors



import org.coroutines._



package object coroutines {

  implicit class ReactorCoroutineOps(val r: Reactor.type) extends AnyVal {
    def coroutine[@spec(Int, Long, Double) T, R](
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
    val receive = coroutine { () =>
      var result = null.asInstanceOf[T]
      val onEvent = (observer: () => Unit) => {
        events.once.onEvent { x =>
          result = x
        }
      }
      yieldval(onEvent)
      result
    }
  }

}
