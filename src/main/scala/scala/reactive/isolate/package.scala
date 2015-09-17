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
        val subs = SubscriptionSet()
        subs += reply.events.once foreach { x =>
          reply.seal()
          emitter.react(x)
          emitter.unreact()
          subs.unsubscribe()
        }
        subs += system.clock.timeout(maxTime) foreach { x =>
          reply.seal()
          emitter.except(new TimeoutException)
          emitter.unreact()
          subs.unsubscribe()
        }
        emitter.withSubscription(subs)
      }

      def retry(x: T, p: S => Boolean, times: Int, minCooldown: Duration): Events[S] = {
        val system = Iso.self.system
        val emitter = new Events.Emitter[S]
        val subs = SubscriptionSet()
        def retry(timesLeft: Int) {
          val reply = request(x, minCooldown)
          def fail() {
            subs.unsubscribe()
            subs += system.clock.timeout(minCooldown) foreach { x =>
              retry(timesLeft - 1)
            }
          }
          subs += reply foreach { x =>
            if (p(x)) emitter.react(x)
            else fail()
          }
          subs += reply handle { case t =>
            fail()
          }
        }
        retry(times)
        emitter.withSubscription(subs)
      }
    }
  }
}
