package io.reactors



import org.coroutines._



package object coroutines {

  implicit class ReactorCoroutineOps(val r: Reactor.type) extends AnyVal {
    def apply[@spec(Int, Long, Double) T, R](
      c: Reactor[T] ~~> ((() => Unit) => Subscription, R)
    ): Proto[Reactor[T]] = {
      Reactor[T] { self =>
        val frame = call(c(self))
        def loop() {
          if (frame.resume) {
            val onEvent = frame.value
            onEvent(loop)
          }
        }
        loop()
      }
    }
  }

  implicit class EventsCoroutineOps[T <: AnyRef](val events: Events[T]) {
    val receive = coroutine { (e: Events[T]) =>
      var result = null.asInstanceOf[T]
      val onEvent = (observer: () => Unit) => {
        e.once.onEvent { x =>
          result = x
        }
      }
      yieldval(onEvent)
      result
    }
  }

}
