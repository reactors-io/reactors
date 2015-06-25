package scala.reactive
package isolate



import org.scalatest._
import org.scalatest.Matchers
import scala.concurrent.duration._



class ServicesTest extends FunSuite with Matchers {

  val system = IsoSystem.default("TestSystem")

  test("Periodical timer should fire 3 times.") {
    system.isolate(Proto[PeriodIso].withScheduler(
      IsoSystem.Bundle.schedulers.piggyback))
  }

}


class PeriodIso extends Iso[Unit] {
  import implicits.canLeak
  var countdown = 3
  system.time.period(50.millis) on {
    countdown -= 1
    if (countdown <= 0) channel.seal()
  }
}
