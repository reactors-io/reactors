package scala.reactive
package examples



import scala.collection._
import scala.concurrent.duration._
import org.scalatest._
import org.scalatest.Matchers



/** Examples from the Reactive Collections frontpage.
 *  
 *  Note: if you are changing this file, please take care to update the website
 *  frontpage.
 */
class FrontpageTest extends FunSuite with Matchers {
  val system = IsoSystem.default("TestSystem")

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
    system.isolate(Proto[UrlIso].withScheduler(IsoSystem.Bundle.schedulers.piggyback))
  }

  test("Requesting server time") {
    val timeServer = system.isolate(Proto[TimeServer])
    val client = system.isolate(Proto[Client](timeServer)
      .withScheduler(IsoSystem.Bundle.schedulers.piggyback))
  }

  test("Establish the rules on a starship") {
    implicit val canLeak = Permission.newCanLeak

    // Store ranks of a starship crew and establish the rules
    val rank = RMap[String, String]
    rank("Geordie") = "Lieutenant"
    rank("Deanna") = "Lieutenant"
    rank("Data") = "Commander Lieutenant"
    rank("Riker") = "Commander"

    val numLt: Signal[Int] =
      rank.filter(_._2 == "Lieutenant").react.size
    val shipRank = Commutoid.apply(("nobody", "no-rank")) {
      (x, y) => if (x._2 < y._2) x else y
    }
    val commandingOfficer: Signal[String] =
      rank.react.commuteFold(shipRank).map(_._1)

    val commanderAndNumLt = (commandingOfficer zip numLt)((m, n) => (m, n))
    commanderAndNumLt.onEvent { case (m, n) =>
      println(s"$m says: all $n lieutenants shall mop floors!")
    }

    rank("Picard") = "Captain"
    assert(commanderAndNumLt() == ("Picard", 2))
    rank("Worf") = "Lieutenant"
    assert(commanderAndNumLt() == ("Picard", 3))
  }

  test("No boxing") {
    import Permission.canBuffer

    // No boxing!
    val cell = RCell[Int](0)
    val evens = cell.filter(_ % 2 == 0).to[RSet[Int]]
    val odds = cell.filter(_ % 2 == 1).to[RSet[Int]]
    cell := 1
    cell := 2
    cell := 3
    cell := 4
    val all = evens union odds
    all.foreach(println)

    val observed = mutable.Set[Int]()
    all.foreach(observed += _)
    assert(observed == (1 to 4).toSet)
  }

}


class UrlIso extends Iso[Unit] {
  import implicits.canLeak

  val timer = system.clock.period(1.second)
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


class TimeServer extends Iso[(String, Channel[Long])] {
  import implicits.canLeak
  events.onCase {
    case ("time", response) =>
      response << System.currentTimeMillis()
      channel.seal()
  }
}


class Client(server: Channel[(String, Channel[Long])])
extends Iso[Long] {
  import implicits.canLeak
  sysEvents.onCase {
    case IsoStarted => server << (("time", channel))
  }
  events.onEvent { time =>
    println("Server Unix time: " + time)
    channel.seal()
  }
}
