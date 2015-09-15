package scala.reactive



import java.util.concurrent.TimeoutException
import scala.concurrent.duration._



package object isolate {
  /** Contains common communication patterns
   */
  object Patterns {
    implicit class ServerChannel[T, S: Arrayable](val c: Channel[(T, Channel[S])]) {
      def request(x: T, maxTime: Duration)(implicit cl: CanLeak): Events[S] = {
        val system = Iso.self.system
        val emitter = new Events.Emitter[S]
        val reply = system.channels.daemon.open[S]
        c ! (x, reply.channel)
        reply.events.once onEvent { x =>
          reply.seal()
          emitter.react(x)
          emitter.unreact()
        }
        system.clock.timeout(maxTime) on {
          reply.seal()
          emitter.except(new TimeoutException)
          emitter.unreact()
        }
        emitter
      }
    }
  }
}
