package scala.reactive
package examples



import scala.concurrent.duration._
import org.scalatest._
import org.scalatest.Matchers



/** Examples from the Reactive Collections frontpage.
 *  
 *  Note: if you are changing this file, please take care to update the website
 *  frontpage.
 */
class FrontpageSuite extends FunSuite with Matchers {

  test("Half-adder reacts to input changes") {
    implicit val canLeak = Permission.newCanLeak

    // Define digital circuits
    def and(a: Signal[Boolean], b: Signal[Boolean]) =
      (a zip b) { _ && _ }
    def xor(a: Signal[Boolean], b: Signal[Boolean]) =
      (a zip b) { _ ^ _ }
    def halfAdder(a: Signal[Boolean], b: Signal[Boolean]) =
      (xor(a, b), and(a, b))
    def logger(name: String, r: Events[Boolean]) =
      r.onEvent(v => println(s"$name: $v"))

    // Simulate a half-adder
    val inputA = RCell(false)
    val inputB = RCell(false)
    val (sum, carry) = halfAdder(inputA, inputB)
    logger("sum", sum)
    logger("carry", carry)
    inputA := true
    assert(sum() == true)
    assert(carry() == false)
    inputB := true
    assert(sum() == false)
    assert(carry() == true)
  }

  test("Fetching contents of a URL or failing after 10 seconds") {
    val system = IsoSystem.default("TestSystem")
    system.isolate(Proto[UrlIso].withScheduler(IsoSystem.Bundle.schedulers.piggyback))
  }

  test("Requesting server time") {
    // TODO
  }

  test("Establish the rules on a starship") {
    // TODO
  }

  test("No boxing") {
    // TODO
  }

}


class UrlIso extends Iso[Unit] {
  import implicits.canLeak

  val timer = system.time.period(1.second)
    .map(_ => 1)
    .scanPast(4)(_ - _)
    .takeWhile(_ >= 0)
  timer.onEvent(println)
  system.net.resource.string("http://www.ietf.org/rfc/rfc1738.txt")
    .map(_.toString)
    .until(timer.unreacted)
    .ivar
    .orElse("Request failed")
    .onEvent { txt =>
      println(txt.take(512) + "...")
      channel.seal()
    }

  sysEvents onCase {
    case IsoTerminated => println("UrlIso terminating...")
  }
}
