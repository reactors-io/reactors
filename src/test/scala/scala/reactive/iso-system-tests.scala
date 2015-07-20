package scala.reactive



import org.scalacheck._
import org.scalacheck.Prop._
import org.scalacheck.Gen._
import org.scalatest.{FunSuite, Matchers}
import scala.concurrent._
import scala.concurrent.duration._



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

}


class TestIso extends Iso[Unit]


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
  events onCase {
    case "success" => p.success(true)
  }
}


class DuringFirstBatchIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  sysEvents onCase {
    case IsoStarted => channel ! "success"
  }
  events onCase {
    case "success" => p.success(true)
  }
}


class DuringFirstEventIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  events onCase {
    case "message" => channel ! "success"
    case "success" => p.success(true)
  }
}


class TwoDuringFirstIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  var countdown = 2
  events onCase {
    case "start" =>
      channel ! "dec"
      channel ! "dec"
    case "dec" =>
      countdown -= 1
      if (countdown == 0) p.success(true)
  }
}


class CountdownIso(val p: Promise[Boolean], var count: Int) extends Iso[String] {
  import implicits.canLeak
  events onCase {
    case "dec" =>
      count -= 1
      if (count == 0) p.success(true)
  }
}


class AfterSealTerminateIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  events onCase {
    case "seal" => default.seal()
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
      channel ! "open"
    case IsoTerminated =>
      p.success(true)
  }
  events onCase {
    case "open" =>
      secondary.channel ! true
      default.seal()
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
      if (left == 0) default.seal()
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
      if (left > 0) channel ! "dummy"
      else default.seal()
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
    case IsoPreempted => default.seal()
    case IsoTerminated => sys.error("Exception thrown during termination!")
  }
}


class RunningExceptionIso(val p: Promise[Throwable]) extends Iso[String] {
  import implicits.canLeak
  events onCase {
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
    case IsoPreempted => default.seal()
  }
}


object Log {
  def apply(msg: String) = println(s"${Thread.currentThread.getName}: $msg")
}


class ManyIso(p: Promise[Boolean], var n: Int) extends Iso[String] {
  val sub = events foreach { v =>
    n -= 1
    if (n <= 0) {
      p.success(true)
      default.seal()
    }
  }
}


abstract class IsoSystemChecks extends Properties("IsoSystem") {

  val system = IsoSystem.default("check-system")

  val scheduler: String

  property("should receive many events") = forAllNoShrink(choose(1, 1024)) { n =>
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[ManyIso](p, n).withScheduler(scheduler))
    for (i <- 0 until n) ch ! "count"
    Await.result(p.future, 4.seconds)
  }

}


object NewThreadIsoSystemChecks extends IsoSystemChecks {
  val scheduler = IsoSystem.Bundle.schedulers.newThread
}
