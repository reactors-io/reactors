package scala.reactive
package isolate



import org.scalatest._
import org.scalatest.Matchers
import scala.concurrent._
import scala.concurrent.duration._



class ServicesTest extends FunSuite with Matchers {

  val system = IsoSystem.default("TestSystem")

  test("Periodical timer should fire 3 times") {
    system.isolate(Proto[PeriodIso].withScheduler(
      IsoSystem.Bundle.schedulers.piggyback))
  }

  test("Timeout should fire exactly once") {
    val timeoutCount = Promise[Int]()
    system.isolate(Proto[TimeoutIso](timeoutCount).withScheduler(
      IsoSystem.Bundle.schedulers.piggyback))
    assert(timeoutCount.future.value.get.get == 1,
      s"Total timeouts: ${timeoutCount.future.value}")
  }

}


class PeriodIso extends Iso[Unit] {
  import implicits.canLeak
  var countdown = 3
  system.clock.period(50.millis) on {
    countdown -= 1
    if (countdown <= 0) channel.seal()
  }
}


class TimeoutIso(val timeoutCount: Promise[Int]) extends Iso[Unit] {
  import implicits.canLeak
  var timeouts = 0
  system.clock.timeout(50.millis) on {
    timeouts += 1
    system.clock.timeout(500.millis) on {
      channel.seal()
      timeoutCount success timeouts
    }
  }
}
