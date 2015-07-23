package scala.reactive



import org.scalacheck._
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Gen.choose
import org.scalatest.{FunSuite, Matchers}
import scala.concurrent._
import scala.concurrent.duration._



class TestIso extends Iso[Unit]


class SelfIso(val p: Promise[Boolean]) extends Iso[Int] {
  import implicits.canLeak
  sysEvents onCase {
    case IsoStarted => p.success(this eq Iso.self)
  }
}


class PromiseIso(val p: Promise[Unit]) extends Iso[Unit] {
  p.success(())
}


class IsoSelfIso(val p: Promise[Boolean]) extends Iso[Unit] {
  if (Iso.self[Iso[_]] eq this) p.success(true)
  else p.success(false)
}


class IsoStartedIso(val p: Promise[Boolean]) extends Iso[Unit] {
  import implicits.canLeak
  sysEvents onCase {
    case IsoStarted => p.success(true)
  }
}


class AfterFirstBatchIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  main.events onCase {
    case "success" => p.success(true)
  }
}


class DuringFirstBatchIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  sysEvents onCase {
    case IsoStarted => main.channel ! "success"
  }
  main.events onCase {
    case "success" => p.success(true)
  }
}


class DuringFirstEventIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  main.events onCase {
    case "message" => main.channel ! "success"
    case "success" => p.success(true)
  }
}


class TwoDuringFirstIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  var countdown = 2
  main.events onCase {
    case "start" =>
      main.channel ! "dec"
      main.channel ! "dec"
    case "dec" =>
      countdown -= 1
      if (countdown == 0) p.success(true)
  }
}


class CountdownIso(val p: Promise[Boolean], var count: Int) extends Iso[String] {
  import implicits.canLeak
  main.events onCase {
    case "dec" =>
      count -= 1
      if (count == 0) p.success(true)
  }
}


class AfterSealTerminateIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  main.events onCase {
    case "seal" => main.seal()
  }
  sysEvents onCase {
    case IsoTerminated => p.success(true)
  }
}


class NewChannelIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  val secondary = system.channels.open[Boolean]
  sysEvents onCase {
    case IsoStarted =>
      main.channel ! "open"
    case IsoTerminated =>
      p.success(true)
  }
  main.events onCase {
    case "open" =>
      secondary.channel ! true
      main.seal()
  }
  secondary.events onEvent { v =>
    secondary.seal()
  }
}


class IsoScheduledIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  var left = 5
  sysEvents onCase {
    case IsoScheduled =>
      left -= 1
      if (left == 0) main.seal()
    case IsoTerminated =>
      p.success(true)
  }
}


class IsoPreemptedIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  var left = 5
  sysEvents onCase {
    case IsoPreempted =>
      left -= 1
      if (left > 0) main.channel ! "dummy"
      else main.seal()
    case IsoTerminated =>
      p.success(true)
  }
}


class CtorExceptionIso(val p: Promise[(Boolean, Boolean)])
extends Iso[Unit] {
  import implicits.canLeak
  var excepted = false
  var terminated = false
  sysEvents onCase {
    case IsoDied(t) =>
      excepted = true
    case IsoTerminated =>
      terminated = true
      p.success((excepted, terminated))
  }
  sys.error("Exception thrown in ctor!")
}


class TerminationExceptionIso(val p: Promise[Boolean]) extends Iso[Unit] {
  import implicits.canLeak
  sysEvents onCase {
    case IsoDied(t) => p.success(true)
    case IsoPreempted => main.seal()
    case IsoTerminated => sys.error("Exception thrown during termination!")
  }
}


class RunningExceptionIso(val p: Promise[Throwable]) extends Iso[String] {
  import implicits.canLeak
  main.events onCase {
    case "die" => sys.error("exception thrown")
  }
  sysEvents onCase {
    case IsoDied(t) => p.success(t)
  }
}


class EventSourceIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  val emitter = new Events.Emitter[Int]()
  emitter onUnreact {
    p.success(true)
  }
  sysEvents onCase {
    case IsoPreempted => main.seal()
  }
}


object Log {
  def apply(msg: String) = println(s"${Thread.currentThread.getName}: $msg")
}


class ManyIso(p: Promise[Boolean], var n: Int) extends Iso[String] {
  val sub = main.events foreach { v =>
    n -= 1
    if (n <= 0) {
      p.success(true)
      main.seal()
    }
  }
}


class EvenOddIso(p: Promise[Boolean], n: Int) extends Iso[Int] {
  import implicits.canLeak
  val rem = RSet[Int]
  for (i <- 0 until n) rem += i
  main.events onEvent { v =>
    if (v % 2 == 0) even.channel ! v
    else odd.channel ! v
  }
  val odd = system.channels.open[Int]
  odd.events onEvent { v =>
    rem -= v
  }
  val even = system.channels.open[Int]
  even.events onEvent { v =>
    rem -= v
  }
  rem.react.size onEvent { sz =>
    if (sz == 0) {
      main.seal()
      odd.seal()
      even.seal()
      p.success(true)
    }
  }
}


class MultiChannelIso(val p: Promise[Boolean], val n: Int) extends Iso[Int] {
  import implicits.canLeak
  var c = n
  val connectors = for (i <- 0 until n) yield {
    val conn = system.channels.open[Int]
    conn.events onEvent { j =>
      if (i == j) conn.seal()
    }
    conn
  }
  main.events onEvent {
    i => connectors(i).channel ! i
  }
  main.events.scanPast(n)((count, _) => count - 1) onEvent { i =>
    main.seal()
  }
  sysEvents onCase {
    case IsoTerminated => p.success(true)
  }
}


class LooperIso(val p: Promise[Boolean], var n: Int) extends Iso[String] {
  import implicits.canLeak
  sysEvents onCase {
    case IsoPreempted =>
      if (n > 0) main.channel ! "count"
      else {
        main.seal()
        p.success(true)
      }
  }
  main.events onCase {
    case "count" => n -= 1
  }
}


class ParentIso(val p: Promise[Boolean], val n: Int, val s: String)
extends Iso[Unit] {
  val ch = system.isolate(Proto[ChildIso](p, n).withScheduler(s))
  for (i <- 0 until n) ch ! i
  main.seal()
}


class ChildIso(val p: Promise[Boolean], val n: Int) extends Iso[Int] {
  var nextNumber = 0
  val sub = main.events foreach { i =>
    if (nextNumber == i) nextNumber += 1
    if (nextNumber == n) {
      main.seal()
      p.success(true)
    }
  }
}


class PingIso(val p: Promise[Boolean], var n: Int, val s: String) extends Iso[String] {
  import implicits.canLeak
  val pong = system.isolate(Proto[PongIso](n, main.channel).withScheduler(s))
  val start = sysEvents onCase {
    case IsoStarted => pong ! "pong"
  }
  val sub = main.events onCase {
    case "ping" =>
      n -= 1
      if (n > 0) pong ! "pong"
      else {
        main.seal()
        p.success(true)
      }
  }
}


class PongIso(var n: Int, val ping: Channel[String]) extends Iso[String] {
  import implicits.canLeak
  main.events onCase {
    case "pong" =>
      ping ! "ping"
      n -= 1
      if (n == 0) main.seal()
  }
}


abstract class BaseIsoSystemCheck(name: String) extends Properties(name) {

  val system = IsoSystem.default("check-system")  

  val scheduler: String

  property("should send itself messages") = forAllNoShrink(choose(1, 1024)) { n =>
    val p = Promise[Boolean]()
    system.isolate(Proto[LooperIso](p, n).withScheduler(scheduler))
    Await.result(p.future, 2.seconds)
  }

}


abstract class IsoSystemCheck(name: String) extends BaseIsoSystemCheck(name) {

  property("should receive many events") = forAllNoShrink(choose(1, 1024)) { n =>
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[ManyIso](p, n).withScheduler(scheduler))
    for (i <- 0 until n) ch ! "count"
    Await.result(p.future, 2.seconds)
  }

  property("should receive many events through different sources") =
    forAllNoShrink(choose(1, 1024)) { n =>
      val p = Promise[Boolean]()
      val ch = system.isolate(Proto[EvenOddIso](p, n).withScheduler(scheduler))
      for (i <- 0 until n) ch ! i
      Await.result(p.future, 2.seconds)
    }

  property("should be terminated after all its channels are sealed") =
    forAllNoShrink(choose(1, 128)) { n =>
      val p = Promise[Boolean]()
      val ch = system.isolate(Proto[MultiChannelIso](p, n).withScheduler(scheduler))
      for (i <- 0 until n) ch ! i
      Await.result(p.future, 2.seconds)
    }

  property("should create another isolate and send it messages") =
    forAllNoShrink(choose(1, 512)) { n =>
      val p = Promise[Boolean]()
      system.isolate(Proto[ParentIso](p, n, scheduler).withScheduler(scheduler))
      Await.result(p.future, 2.seconds)
    }

  property("should play ping-pong with another isolate") =
    forAllNoShrink(choose(1, 512)) { n =>
      val p = Promise[Boolean]()
      system.isolate(Proto[PingIso](p, n, scheduler).withScheduler(scheduler))
      Await.result(p.future, 2.second)
    }

}


object NewThreadIsoSystemCheck extends IsoSystemCheck("NewThreadSystem") {
  val scheduler = IsoSystem.Bundle.schedulers.newThread
}


object GlobalExecutionContextIsoSystemCheck extends IsoSystemCheck("ECSystem") {
  val scheduler = IsoSystem.Bundle.schedulers.globalExecutionContext
}


object DefaultSchedulerIsoSystemCheck extends IsoSystemCheck("DefaultSchedulerSystem") {
  val scheduler = IsoSystem.Bundle.schedulers.default
}


object PiggybackIsoSystemCheck extends BaseIsoSystemCheck("PiggybackSystem") {
  val scheduler = IsoSystem.Bundle.schedulers.piggyback
}


class IsoSystemTest extends FunSuite with Matchers {

  test("system should return without throwing") {
    val system = IsoSystem.default("test")
    val proto = Proto[TestIso]
    system.isolate(proto)
    assert(system.frames.forName("isolate-0") != null)
  }

  test("system should return without throwing and use custom name") {
    val system = IsoSystem.default("test")
    val proto = Proto[TestIso].withName("Izzy")
    system.isolate(proto)
    assert(system.frames.forName("Izzy") != null)
    assert(system.frames.forName("Izzy").name == "Izzy")
  }

  test("system should throw when attempting to reuse the same name") {
    val system = IsoSystem.default("test")
    system.isolate(Proto[TestIso].withName("Izzy"))
    intercept[IllegalArgumentException] {
      system.isolate(Proto[TestIso].withName("Izzy"))
    }
  }

  test("system should create a default channel for the isolate") {
    val system = IsoSystem.default("test")
    val channel = system.isolate(Proto[TestIso].withName("Izzy"))
    assert(channel != null)
    val conn = system.frames.forName("Izzy").connectors.forName("default")
    assert(conn != null)
    assert(conn.channel eq channel)
    assert(!conn.isDaemon)
  }

  test("system should create a system channel for the isolate") {
    val system = IsoSystem.default("test")
    system.isolate(Proto[TestIso].withName("Izzy"))
    val conn = system.frames.forName("Izzy").connectors.forName("system")
    assert(conn != null)
    assert(conn.isDaemon)
  }

  test("system should schedule isolate's ctor for execution") {
    val system = IsoSystem.default("test")
    val p = Promise[Unit]()
    system.isolate(Proto[PromiseIso](p))
    Await.result(p.future, 5.seconds)
  }

  test("system should invoke the ctor with the Iso.self set") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    system.isolate(Proto[IsoSelfIso](p))
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should ensure the IsoStarted event") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    system.isolate(Proto[IsoStartedIso](p))
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should process an event that arrives after the first batch") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[AfterFirstBatchIso](p))
    Thread.sleep(250)
    ch ! "success"
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should process an event that arrives during the first batch") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[DuringFirstBatchIso](p))
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should process an event that arrives during the first event") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[DuringFirstEventIso](p))
    ch ! "message"
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should process two events that arrive during the first event") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[TwoDuringFirstIso](p))
    ch ! "start"
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should process 100 incoming events") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[CountdownIso](p, 100))
    Thread.sleep(250)
    for (i <- 0 until 100) ch ! "dec"
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should terminate after sealing its channel") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[AfterSealTerminateIso](p))
    ch ! "seal"
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should be able to open a new channel") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    system.isolate(Proto[NewChannelIso](p))
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should get IsoScheduled events") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[IsoScheduledIso](p))
    for (i <- 0 until 5) {
      Thread.sleep(60)
      ch ! "dummy"
    }
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should get IsoPreempted events") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    system.isolate(Proto[IsoPreemptedIso](p))
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should terminate on ctor exception") {
    val system = IsoSystem.default("test")
    val p = Promise[(Boolean, Boolean)]()
    system.isolate(Proto[CtorExceptionIso](p))
    assert(Await.result(p.future, 5.seconds) == (true, true))
  }

  test("iso does not raise termination-related IsoDied events after IsoTerminated") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    system.isolate(Proto[TerminationExceptionIso](p))
    Thread.sleep(100)
    assert(p.future.value == None)
  }

  test("iso should terminate on exceptions while running") {
    val system = IsoSystem.default("test")
    val p = Promise[Throwable]()
    val ch = system.isolate(Proto[RunningExceptionIso](p))
    ch ! "die"
    assert(Await.result(p.future, 5.seconds).getMessage == "exception thrown")
  }

  test("Iso.self should be correctly set") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    system.isolate(Proto[SelfIso](p))
    assert(Await.result(p.future, 5.seconds))
  }

}
