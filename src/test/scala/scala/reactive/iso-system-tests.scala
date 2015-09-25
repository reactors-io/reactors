package scala.reactive



import org.scalacheck._
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Gen.choose
import org.scalatest.{FunSuite, Matchers}
import scala.annotation.unchecked
import scala.collection._
import scala.concurrent._
import scala.concurrent.duration._



class TestIso extends Iso[Unit]


class SelfIso(val p: Promise[Boolean]) extends Iso[Int] {
  import implicits.canLeak
  sysEvents onMatch {
    case IsoStarted => p.success(this eq Iso.self)
  }
}


class PiggyIso(val p: Promise[Boolean]) extends Iso[Unit] {
  import implicits.canLeak
  sysEvents onMatch {
    case IsoStarted =>
      try {
        val piggy = IsoSystem.Bundle.schedulers.piggyback
        system.isolate(Proto[SelfIso].withScheduler(piggy))
      } catch {
        case e: IllegalStateException =>
          p.success(true)
      } finally {
        main.seal()
      }
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
  sysEvents onMatch {
    case IsoStarted => p.success(true)
  }
}


class AfterFirstBatchIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  main.events onMatch {
    case "success" => p.success(true)
  }
}


class DuringFirstBatchIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  sysEvents onMatch {
    case IsoStarted => main.channel ! "success"
  }
  main.events onMatch {
    case "success" => p.success(true)
  }
}


class DuringFirstEventIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  main.events onMatch {
    case "message" => main.channel ! "success"
    case "success" => p.success(true)
  }
}


class TwoDuringFirstIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  var countdown = 2
  main.events onMatch {
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
  main.events onMatch {
    case "dec" =>
      count -= 1
      if (count == 0) p.success(true)
  }
}


class AfterSealTerminateIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  main.events onMatch {
    case "seal" => main.seal()
  }
  sysEvents onMatch {
    case IsoTerminated => p.success(true)
  }
}


class NewChannelIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  val secondary = system.channels.open[Boolean]
  sysEvents onMatch {
    case IsoStarted =>
      main.channel ! "open"
    case IsoTerminated =>
      p.success(true)
  }
  main.events onMatch {
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
  sysEvents onMatch {
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
  sysEvents onMatch {
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
  sysEvents onMatch {
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
  sysEvents onMatch {
    case IsoDied(t) => p.success(true)
    case IsoPreempted => main.seal()
    case IsoTerminated => sys.error("Exception thrown during termination!")
  }
}


class RunningExceptionIso(val p: Promise[Throwable]) extends Iso[String] {
  import implicits.canLeak
  main.events onMatch {
    case "die" => sys.error("exception thrown")
  }
  sysEvents onMatch {
    case IsoDied(t) => p.success(t)
  }
}


class EventSourceIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  val emitter = new Events.Emitter[Int]()
  emitter onUnreact {
    p.success(true)
  }
  sysEvents onMatch {
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
    if (i == 0) main.seal()
  }
  sysEvents onMatch {
    case IsoTerminated => p.success(true)
  }
}


class LooperIso(val p: Promise[Boolean], var n: Int) extends Iso[String] {
  import implicits.canLeak
  sysEvents onMatch {
    case IsoPreempted =>
      if (n > 0) main.channel ! "count"
      else {
        main.seal()
        p.success(true)
      }
  }
  main.events onMatch {
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
  val start = sysEvents onMatch {
    case IsoStarted => pong ! "pong"
  }
  val sub = main.events onMatch {
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
  main.events onMatch {
    case "pong" =>
      ping ! "ping"
      n -= 1
      if (n == 0) main.seal()
  }
}


class RingIso(
  val index: Int,
  val num: Int,
  val sink: Either[Promise[Boolean], Channel[String]],
  val sched: String
) extends Iso[String] {
  import implicits.canLeak

  val next: Channel[String] = {
    if (index == 0) {
      val p = Proto[RingIso](index + 1, num, Right(main.channel), sched)
        .withScheduler(sched)
      system.isolate(p)
    } else if (index < num) {
      val p = Proto[RingIso](index + 1, num, sink, sched).withScheduler(sched)
      system.isolate(p)
    } else {
      sink match {
        case Right(first) => first
        case _ => sys.error("unexpected case")
      }
    }
  }

  main.events onMatch {
    case "start" =>
      next ! "ping"
    case "ping" =>
      next ! "ping"
      main.seal()
      if (index == 0) sink match {
        case Left(p) => p.success(true)
        case _ => sys.error("unexpected case")
      }
  }
}


class TerminatedIso(val p: Promise[Boolean]) extends Iso[Unit] {
  import implicits.canLeak
  sysEvents onMatch {
    case IsoStarted =>
      main.seal()
    case IsoTerminated =>
      // should still be different than null
      p.success(system.frames.forName("ephemo") != null)
  }
}


class LookupChannelIso(val started: Promise[Boolean], val ended: Promise[Boolean])
extends Iso[Unit] {
  import implicits.canLeak
  sysEvents onMatch {
    case IsoStarted =>
      val terminator = system.channels.daemon.named("terminator").open[String]
      terminator.events onMatch {
        case "end" =>
          main.seal()
          ended.success(true)
      }
      started.success(true)
  }
}


class ChannelsAskIso(val p: Promise[Boolean]) extends Iso[Unit] {
  import implicits.canLeak
  val answer = system.channels.daemon.open[Option[Channel[_]]]
  system.iso.resolver ! (("chaki#main", answer.channel))
  answer.events onMatch {
    case Some(ch: Channel[Unit] @unchecked) => ch ! (())
    case None => sys.error("chaki#main not found")
  }
  main.events on {
    main.seal()
    p.success(true)
  }
}


class RequestIso(val p: Promise[Boolean]) extends Iso[Unit] {
  import implicits.canLeak
  import patterns._
  import scala.concurrent.duration._
  val server = system.channels.daemon.open[(String, Channel[String])]
  server.events onMatch {
    case ("request", r) => r ! "reply"
  }
  sysEvents onMatch {
    case IsoStarted =>
      server.channel.request("request", 2.seconds) onMatch {
        case "reply" =>
          main.seal()
          p.success(true)
      }
  }
}


class TimeoutRequestIso(val p: Promise[Boolean]) extends Iso[Unit] {
  import implicits.canLeak
  import patterns._
  import scala.concurrent.duration._
  val server = system.channels.daemon.open[(String, Channel[String])]
  server.events onMatch {
    case ("request", r) => // staying silent
  }
  sysEvents onMatch {
    case IsoStarted =>
      server.channel.request("request", 1.seconds) onExcept {
        case t =>
          main.seal()
          p.success(true)
      }
  }
}


class SecondRetryAfterTimeoutIso(val p: Promise[Int]) extends Iso[Unit] {
  import implicits.canLeak
  import patterns._
  val requests = system.channels.daemon.open[(String, Channel[String])]
  var firstRequest = true
  var numAttempts = 0
  def test(x: String) = {
    numAttempts += 1
    true
  }
  requests.events onMatch {
    case ("try", answer) =>
      if (firstRequest) firstRequest = false
      else answer ! "yes"
  }
  sysEvents onMatch {
    case IsoStarted =>
      requests.channel.retry("try", test, 4, 200.millis) onMatch {
        case "yes" => p.success(numAttempts)
      }
  }
}


class SecondRetryAfterDropIso(val p: Promise[Boolean]) extends Iso[Unit] {
  import implicits.canLeak
  import patterns._
  val requests = system.channels.daemon.open[(String, Channel[String])]
  var numReplies = 0
  requests.events onMatch {
    case ("try", answer) => answer ! "yes"
  }
  def test(x: String) = {
    numReplies += 1
    numReplies == 2
  }
  sysEvents onMatch {
    case IsoStarted =>
      requests.channel.retry("try", test, 4, 200.millis) onMatch {
        case "yes" => p.success(true)
      }
  }
}


class FailedRetryIso(val p: Promise[Boolean]) extends Iso[Unit] {
  import implicits.canLeak
  import patterns._
  val requests = system.channels.daemon.open[(String, Channel[String])]
  requests.events onMatch {
    case ("try", answer) => answer ! "yes"
  }
  sysEvents onMatch {
    case IsoStarted =>
      requests.channel.retry("try", _ => false, 4, 50.millis).onReaction(
        new Reactor[String] {
          def react(x: String) = p.success(false)
          def except(t: Throwable) = t match {
            case r: RuntimeException => p.success(true)
            case _ => p.success(false)
          }
          def unreact() = {}
        })
  }
}


class NameFinderIso extends Iso[Unit] {
  import implicits.canLeak
  import patterns._
  system.iso.resolver.retry("fluffy#main", _ != None, 20, 50.millis) onMatch {
    case Some(ch: Channel[String] @unchecked) =>
      ch ! "die"
      main.seal()
  }
}


class NamedIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  main.events onMatch {
    case "die" =>
      main.seal()
      p.success(true)
  }
}


class SysEventsIso(val p: Promise[Boolean]) extends Iso[String] {
  import implicits.canLeak
  val events = mutable.Buffer[SysEvent]()
  sysEvents onMatch {
    case IsoTerminated =>
      p.trySuccess(events == Seq(IsoStarted, IsoScheduled, IsoPreempted))
    case e =>
      events += e
      if (events.size == 3) main.seal()
  }
}


abstract class BaseIsoSystemCheck(name: String) extends Properties(name) {

  val system = IsoSystem.default("check-system")  

  val scheduler: String

  property("should send itself messages") = forAllNoShrink(choose(1, 1024)) { n =>
    val p = Promise[Boolean]()
    system.isolate(Proto[LooperIso](p, n).withScheduler(scheduler))
    Await.result(p.future, 10.seconds)
  }

}


abstract class IsoSystemCheck(name: String) extends BaseIsoSystemCheck(name) {

  property("should receive many events") = forAllNoShrink(choose(1, 1024)) { n =>
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[ManyIso](p, n).withScheduler(scheduler))
    for (i <- 0 until n) ch ! "count"
    Await.result(p.future, 10.seconds)
  }

  property("should receive many events through different sources") =
    forAllNoShrink(choose(1, 1024)) { n =>
      val p = Promise[Boolean]()
      val ch = system.isolate(Proto[EvenOddIso](p, n).withScheduler(scheduler))
      for (i <- 0 until n) ch ! i
      Await.result(p.future, 10.seconds)
    }

  property("should be terminated after all its channels are sealed") =
    forAllNoShrink(choose(1, 128)) { n =>
      val p = Promise[Boolean]()
      val ch = system.isolate(Proto[MultiChannelIso](p, n).withScheduler(scheduler))
      for (i <- 0 until n) {
        Thread.sleep(0)
        ch ! i
      }
      Await.result(p.future, 10.seconds)
    }

  property("should create another isolate and send it messages") =
    forAllNoShrink(choose(1, 512)) { n =>
      val p = Promise[Boolean]()
      system.isolate(Proto[ParentIso](p, n, scheduler).withScheduler(scheduler))
      Await.result(p.future, 10.seconds)
    }

  property("should play ping-pong with another isolate") =
    forAllNoShrink(choose(1, 512)) { n =>
      val p = Promise[Boolean]()
      system.isolate(Proto[PingIso](p, n, scheduler).withScheduler(scheduler))
      Await.result(p.future, 10.seconds)
    }

  property("a ring of isolates should correctly propagate messages") =
    forAllNoShrink(choose(1, 64)) { n =>
      val p = Promise[Boolean]()
      val proto = Proto[RingIso](0, n, Left(p), scheduler).withScheduler(scheduler)
      val ch = system.isolate(proto)
      ch ! "start"
      Await.result(p.future, 10.seconds)
    }

  property("should receive all possible system events") =
    forAllNoShrink(choose(1, 128)) { n =>
      val p = Promise[Boolean]()
      val proto = Proto[SysEventsIso](p).withScheduler(scheduler)
      system.isolate(proto)
      Await.result(p.future, 10.seconds)
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
    assert(system.frames.forName("isolate-1") != null)
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
    val conn = system.frames.forName("Izzy").connectors.forName("main")
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
    Await.result(p.future, 10.seconds)
  }

  test("system should invoke the ctor with the Iso.self set") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    system.isolate(Proto[IsoSelfIso](p))
    assert(Await.result(p.future, 10.seconds))
  }

  test("iso should ensure the IsoStarted event") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    system.isolate(Proto[IsoStartedIso](p))
    assert(Await.result(p.future, 10.seconds))
  }

  test("iso should process an event that arrives after the first batch") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[AfterFirstBatchIso](p))
    Thread.sleep(250)
    ch ! "success"
    assert(Await.result(p.future, 10.seconds))
  }

  test("iso should process an event that arrives during the first batch") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[DuringFirstBatchIso](p))
    assert(Await.result(p.future, 10.seconds))
  }

  test("iso should process an event that arrives during the first event") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[DuringFirstEventIso](p))
    ch ! "message"
    assert(Await.result(p.future, 10.seconds))
  }

  test("iso should process two events that arrive during the first event") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[TwoDuringFirstIso](p))
    ch ! "start"
    assert(Await.result(p.future, 10.seconds))
  }

  test("iso should process 100 incoming events") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[CountdownIso](p, 100))
    Thread.sleep(250)
    for (i <- 0 until 100) ch ! "dec"
    assert(Await.result(p.future, 10.seconds))
  }

  test("iso should terminate after sealing its channel") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[AfterSealTerminateIso](p))
    ch ! "seal"
    assert(Await.result(p.future, 10.seconds))
  }

  test("iso should be able to open a new channel") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    system.isolate(Proto[NewChannelIso](p))
    assert(Await.result(p.future, 10.seconds))
  }

  test("iso should get IsoScheduled events") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[IsoScheduledIso](p))
    for (i <- 0 until 5) {
      Thread.sleep(60)
      ch ! "dummy"
    }
    assert(Await.result(p.future, 10.seconds))
  }

  test("iso should get IsoPreempted events") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    system.isolate(Proto[IsoPreemptedIso](p))
    assert(Await.result(p.future, 10.seconds))
  }

  test("iso should terminate on ctor exception") {
    val system = IsoSystem.default("test")
    val p = Promise[(Boolean, Boolean)]()
    system.isolate(Proto[CtorExceptionIso](p))
    assert(Await.result(p.future, 10.seconds) == (true, true))
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
    assert(Await.result(p.future, 10.seconds).getMessage == "exception thrown")
  }

  test("Iso.self should be correctly set") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    system.isolate(Proto[SelfIso](p))
    assert(Await.result(p.future, 10.seconds))
  }

  test("piggyback scheduler should throw an exception if called from an isolate") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    system.isolate(Proto[PiggyIso](p))
    assert(Await.result(p.future, 10.seconds))
  }

  test("after termination and before IsoTerminated, isolate name should be released") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]()
    system.isolate(Proto[TerminatedIso](p).withName("ephemo"))
    assert(Await.result(p.future, 10.seconds))
    assert(system.frames.forName("ephemo") == null)
  }

  test("after the iso starts, its channel should be looked up") {
    val system = IsoSystem.default("test")
    val started = Promise[Boolean]()
    val ended = Promise[Boolean]()
    val channel = system.isolate(Proto[LookupChannelIso](started, ended).withName("pi"))
    assert(Await.result(started.future, 10.seconds))
    system.channels.find[String]("pi#terminator") match {
      case Some(ch) => ch ! "end"
      case None => sys.error("channel not found")
    }
    assert(Await.result(ended.future, 10.seconds))
  }

  test("channels iso should look up channels when asked") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]
    system.isolate(Proto[ChannelsAskIso](p).withName("chaki"))
    assert(Await.result(p.future, 10.seconds))
  }

  test("request should return the result once") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]
    system.isolate(Proto[RequestIso](p))
    assert(Await.result(p.future, 10.seconds))
  }

  test("request should timeout once") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]
    system.isolate(Proto[TimeoutRequestIso](p))
    assert(Await.result(p.future, 10.seconds))
  }

  test("retry should succeed the second time after timeout") {
    val system = IsoSystem.default("test")
    val p = Promise[Int]
    system.isolate(Proto[SecondRetryAfterTimeoutIso](p))
    assert(Await.result(p.future, 10.seconds) == 1)
  }

  test("retry should succeed the second time after dropping reply") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]
    system.isolate(Proto[SecondRetryAfterDropIso](p))
    assert(Await.result(p.future, 10.seconds))
  }

  test("retry should fail after 5 retries") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]
    system.isolate(Proto[FailedRetryIso](p))
    assert(Await.result(p.future, 10.seconds))
  }

  test("should resolve name before failing") {
    val system = IsoSystem.default("test")
    val p = Promise[Boolean]
    system.isolate(Proto[NameFinderIso])
    Thread.sleep(100)
    system.isolate(Proto[NamedIso](p).withName("fluffy"))
    assert(Await.result(p.future, 10.seconds))
  }

}
