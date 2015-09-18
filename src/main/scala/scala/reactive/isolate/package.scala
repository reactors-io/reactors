package scala.reactive



import java.util.concurrent.TimeoutException
import scala.concurrent.duration._



package object isolate {
  /** Contains common communication patterns
   */
  object Patterns {
    implicit class ServerChannel[T, S: Arrayable](val c: Channel[(T, Channel[S])]) {
      def request(x: T, maxTime: Duration): Events[S] = {
        val system = Iso.self.system
        val emitter = new Events.Emitter[S]
        val reply = system.channels.daemon.open[S]
        c ! (x, reply.channel)
        implicit val canLeak = Permission.newCanLeak
        reply.events.once onEvent { x =>
          reply.seal()
          emitter.react(x)
          emitter.unreact()
          canLeak.unsubscribe()
        }
        system.clock.timeout(maxTime) on {
          reply.seal()
          emitter.except(new TimeoutException)
          emitter.unreact()
          canLeak.unsubscribe()
        }
        emitter.withSubscription(canLeak)
      }

      def retry(x: T, p: S => Boolean, times: Int, minCooldown: Duration): Events[S] = {
        val system = Iso.self.system
        val emitter = new Events.Emitter[S]
        implicit val canLeak = Permission.newCanLeak
        def retry(timesLeft: Int) {
          val reply = request(x, minCooldown)
          def fail() {
            canLeak.unsubscribe()
            system.clock.timeout(minCooldown) on {
              retry(timesLeft - 1)
            }
          }
          if (timesLeft == 0) {
            emitter.except(new RuntimeException(s"Timed out after $times retries."))
            emitter.unreact()
          } else {
            val r = new Reactor[S] {
              def react(x: S) = {
                if (p(x)) {
                  emitter.react(x)
                  emitter.unreact()
                } else fail()
              }
              def except(t: Throwable) = fail()
              def unreact() = {}
            }
            reply.onReaction(r)
          }
        }
        retry(times)
        emitter.withSubscription(canLeak)
      }
    }
  }
}
