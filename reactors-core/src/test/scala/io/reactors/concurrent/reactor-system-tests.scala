package io.reactors
package concurrent



import org.scalacheck._
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Gen.choose
import org.scalatest.{FunSuite, Matchers}
import scala.annotation.unchecked
import scala.collection._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Success



class SelfReactor(val p: Promise[Boolean]) extends Reactor[Int] {
  sysEvents onMatch {
    case ReactorStarted => p.success(this eq Reactor.self)
  }
}


class PiggyReactor(val p: Promise[Boolean]) extends Reactor[Unit] {
  sysEvents onMatch {
    case ReactorStarted =>
      try {
        val piggy = ReactorSystem.Bundle.schedulers.piggyback
        system.spawn(Proto[SelfReactor].withScheduler(piggy))
      } catch {
        case e: IllegalStateException =>
          p.success(true)
      } finally {
        main.seal()
      }
  }
}


class PromiseReactor(val p: Promise[Unit]) extends Reactor[Unit] {
  p.success(())
}


class ReactorSelfReactor(val p: Promise[Boolean]) extends Reactor[Unit] {
  if (Reactor.self[Reactor[_]] eq this) p.success(true)
  else p.success(false)
}


class ReactorStartedReactor(val p: Promise[Boolean]) extends Reactor[Unit] {
  sysEvents onMatch {
    case ReactorStarted => p.success(true)
  }
}


class AfterFirstBatchReactor(val p: Promise[Boolean]) extends Reactor[String] {
  main.events onMatch {
    case "success" => p.success(true)
  }
}


class DuringFirstBatchReactor(val p: Promise[Boolean]) extends Reactor[String] {
  sysEvents onMatch {
    case ReactorStarted => main.channel ! "success"
  }
  main.events onMatch {
    case "success" => p.success(true)
  }
}


class DuringFirstEventReactor(val p: Promise[Boolean]) extends Reactor[String] {
  main.events onMatch {
    case "message" => main.channel ! "success"
    case "success" => p.success(true)
  }
}


class TwoDuringFirstReactor(val p: Promise[Boolean]) extends Reactor[String] {
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


class CountdownPromiseReactor(val p: Promise[Boolean], var count: Int)
extends Reactor[String] {
  main.events onMatch {
    case "dec" =>
      count -= 1
      if (count == 0) p.success(true)
  }
}


class AfterSealTerminateReactor(val p: Promise[Boolean]) extends Reactor[String] {
  main.events onMatch {
    case "seal" => main.seal()
  }
  sysEvents onMatch {
    case ReactorTerminated => p.success(true)
  }
}


class NewChannelReactor(val p: Promise[Boolean]) extends Reactor[String] {
  val secondary = system.channels.open[Boolean]
  sysEvents onMatch {
    case ReactorStarted =>
      main.channel ! "open"
    case ReactorTerminated =>
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


class ReactorScheduledReactor(val p: Promise[Boolean]) extends Reactor[String] {
  var left = 5
  sysEvents onMatch {
    case ReactorScheduled =>
      left -= 1
      if (left == 0) main.seal()
    case ReactorTerminated =>
      p.success(true)
  }
}


class ReactorPreemptedReactor(val p: Promise[Boolean]) extends Reactor[String] {
  var left = 5
  sysEvents onMatch {
    case ReactorPreempted =>
      left -= 1
      if (left > 0) main.channel ! "dummy"
      else if (left == 0) main.seal()
    case ReactorTerminated =>
      p.success(true)
  }
}


class CtorExceptionReactor(val p: Promise[(Boolean, Boolean)])
extends Reactor[Unit] {
  var excepted = false
  var terminated = false
  sysEvents onMatch {
    case ReactorDied(t) =>
      excepted = true
    case ReactorTerminated =>
      terminated = true
      p.success((excepted, terminated))
  }
  sys.error("Exception thrown in ctor!")
}


class TerminationExceptionReactor(val p: Promise[Boolean]) extends Reactor[Unit] {
  sysEvents onMatch {
    case ReactorDied(t) => p.success(true)
    case ReactorPreempted => main.seal()
    case ReactorTerminated => sys.error("Exception thrown during termination!")
  }
}


class RunningExceptionReactor(val p: Promise[Throwable]) extends Reactor[String] {
  main.events onMatch {
    case "die" => sys.error("exception thrown")
  }
  sysEvents onMatch {
    case ReactorDied(t) => p.success(t)
  }
}


class EventSourceReactor(val p: Promise[Boolean]) extends Reactor[String] {
  val emitter = new Events.Emitter[Int]()
  emitter onDone {
    p.success(true)
  }
  sysEvents onMatch {
    case ReactorPreempted => main.seal()
  }
}


object Log {
  def apply(msg: String) = println(s"${Thread.currentThread.getName}: $msg")
}


class RingReactor(
  val index: Int,
  val num: Int,
  val sink: Either[Promise[Boolean], Channel[String]],
  val sched: String
) extends Reactor[String] {

  val next: Channel[String] = {
    if (index == 0) {
      val p = Proto[RingReactor](index + 1, num, Right(main.channel), sched)
        .withScheduler(sched)
      system.spawn(p)
    } else if (index < num) {
      val p = Proto[RingReactor](index + 1, num, sink, sched).withScheduler(sched)
      system.spawn(p)
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


class TerminatedReactor(val p: Promise[Boolean]) extends Reactor[Unit] {
  sysEvents onMatch {
    case ReactorStarted =>
      main.seal()
    case ReactorTerminated =>
      // should still be different than null
      p.success(system.frames.forName("ephemo") != null)
  }
}


class LookupChannelReactor(val started: Promise[Boolean], val ended: Promise[Boolean])
extends Reactor[Unit] {
  sysEvents onMatch {
    case ReactorStarted =>
      val terminator = system.channels.daemon.named("terminator").open[String]
      terminator.events onMatch {
        case "end" =>
          main.seal()
          ended.success(true)
      }
      started.success(true)
  }
}


class ChannelsAskReactor(val p: Promise[Boolean]) extends Reactor[Unit] {
  val answer = system.channels.daemon.open[Option[Channel[_]]]
  system.names.resolve ! (("chaki#main", answer.channel))
  answer.events onMatch {
    case Some(ch: Channel[Unit] @unchecked) => ch ! (())
    case None => sys.error("chaki#main not found")
  }
  main.events on {
    main.seal()
    p.success(true)
  }
}


class NamedReactor(val p: Promise[Boolean]) extends Reactor[String] {
  main.events onMatch {
    case "die" =>
      main.seal()
      p.success(true)
  }
}


abstract class BaseReactorSystemCheck(name: String) extends Properties(name) {

  val system = ReactorSystem.default("check-system")

  val scheduler: String

  property("should send itself messages") = forAllNoShrink(choose(1, 1024)) { num =>
    val p = Promise[Boolean]()
    var n = num
    val proto = Reactor[String] { self =>
      self.sysEvents onMatch {
        case ReactorPreempted =>
          if (n > 0) self.main.channel ! "count"
          else {
            if (self.main.seal()) p.success(true)
          }
      }
      self.main.events onMatch {
        case "count" => n -= 1
      }
    }
    system.spawn(proto.withScheduler(scheduler))
    Await.result(p.future, 10.seconds)
  }

}


abstract class ReactorSystemCheck(name: String) extends BaseReactorSystemCheck(name) {

  property("should receive many events") = forAllNoShrink(choose(1, 1024)) { num =>
    val p = Promise[Boolean]()
    var n = num
    val proto = Reactor[String] { self =>
      val sub = self.main.events onEvent { v =>
        n -= 1
        if (n <= 0) {
          p.success(true)
          self.main.seal()
        }
      }
    }
    val ch = system.spawn(proto.withScheduler(scheduler))
    for (i <- 0 until n) ch ! "count"
    Await.result(p.future, 10.seconds)
  }

  property("should be executed by at most one thread at a time") =
    forAllNoShrink(choose(1, 32000)) { n =>
      val count = Promise[Int]()
      val done = Promise[Boolean]()
      val proto = Reactor[String] { self =>
        var threadCount = 0
        var left = n
        self.main.events onMatch {
          case "dec" =>
            if (threadCount != 1) count.success(threadCount)
            left -= 1
            if (left == 0) self.main.seal()
        }
        self.sysEvents onMatch {
          case ReactorScheduled => threadCount += 1
          case ReactorPreempted => threadCount -= 1
          case ReactorTerminated => done.success(true)
        }
      }
      val ch = system.spawn(proto.withScheduler(scheduler))
      for (group <- (0 until n).grouped(n / 4 + 1)) Future {
        for (e <- group) ch ! "dec"
      }
      Await.ready(done.future, 10.seconds)
      assert(!count.future.value.isInstanceOf[Some[_]], count.future.value)
      done.future.value == Some(Success(true))
    }

  property("should receive many events through different sources") =
    forAllNoShrink(choose(1, 1024)) { n =>
      val p = Promise[Boolean]()
      val proto = Reactor[Int] { self =>
        new Reactor.Placeholder {
          val rem = mutable.Set[Int]()
          for (i <- 0 until n) rem += i
          val odd = self.system.channels.open[Int]
          odd.events onEvent { v =>
            rem -= v
            check()
          }
          val even = self.system.channels.open[Int]
          even.events onEvent { v =>
            rem -= v
            check()
          }
          def check() {
            if (rem.size == 0) {
              self.main.seal()
              odd.seal()
              even.seal()
              p.success(true)
            }
          }
          self.main.events onEvent { v =>
            if (v % 2 == 0) even.channel ! v
            else odd.channel ! v
          }
        }
      }
      val ch = system.spawn(proto.withScheduler(scheduler))
      for (i <- 0 until n) ch ! i
      Await.result(p.future, 10.seconds)
    }

  property("should be terminated after all its channels are sealed") =
    forAllNoShrink(choose(1, 128)) { n =>
      val p = Promise[Boolean]()
      val proto = Reactor[Int] { self =>
        var c = n
        val connectors = for (i <- 0 until n) yield {
          val conn = self.system.channels.open[Int]
          conn.events onEvent { j =>
            if (i == j) conn.seal()
          }
          conn
        }
        self.main.events onEvent {
          i => connectors(i).channel ! i
        }
        self.main.events.scanPast(n)((count, _) => count - 1) onEvent { i =>
          if (i == 0) self.main.seal()
        }
        self.sysEvents onMatch {
          case ReactorTerminated => p.success(true)
        }
      }
      val ch = system.spawn(proto.withScheduler(scheduler))
      for (i <- 0 until n) {
        Thread.sleep(0)
        ch ! i
      }
      Await.result(p.future, 10.seconds)
    }

  property("should create another reactor and send it messages") =
    forAllNoShrink(choose(1, 512)) { n =>
      val p = Promise[Boolean]()
      val s = scheduler
      val proto = Reactor[Unit] { self =>
        val proto = Reactor[Int] { self =>
          var nextNumber = 0
          val sub = self.main.events onEvent { i =>
            if (nextNumber == i) nextNumber += 1
            if (nextNumber == n) {
              self.main.seal()
              p.success(true)
            }
          }
        }
        val ch = self.system.spawn(proto.withScheduler(s))
        for (i <- 0 until n) ch ! i
        self.main.seal()
      }
      system.spawn(proto.withScheduler(scheduler))
      Await.result(p.future, 10.seconds)
    }

  property("should play ping-pong with another reactor") =
    forAllNoShrink(choose(1, 512)) { num =>
      val p = Promise[Boolean]()
      val s = scheduler
      var n = num
      def pongProto(ping: Channel[String]): Proto[Reactor[String]] = Reactor[String] {
        self =>
        var n = num
        self.main.events onMatch {
          case "pong" =>
            ping ! "ping"
            n -= 1
            if (n == 0) self.main.seal()
        }
      }
      val pingProto = Reactor[String] { self =>
        val pong = self.system.spawn(pongProto(self.main.channel).withScheduler(s))
        val start = self.sysEvents onMatch {
          case ReactorStarted => pong ! "pong"
        }
        val sub = self.main.events onMatch {
          case "ping" =>
            n -= 1
            if (n > 0) pong ! "pong"
            else {
              self.main.seal()
              p.success(true)
            }
        }
      }
      system.spawn(pingProto.withScheduler(scheduler))
      Await.result(p.future, 10.seconds)
    }

  property("a ring of isolates should correctly propagate messages") =
    forAllNoShrink(choose(1, 64)) { n =>
      val p = Promise[Boolean]()
      val proto = Proto[RingReactor](0, n, Left(p), scheduler).withScheduler(scheduler)
      val ch = system.spawn(proto)
      ch ! "start"
      Await.result(p.future, 10.seconds)
    }

  property("should receive all system events") =
    forAllNoShrink(choose(1, 128)) { n =>
      val p = Promise[Boolean]()
      val proto = Reactor[String] { self =>
        val events = mutable.Buffer[SysEvent]()
        self.sysEvents onMatch {
          case ReactorTerminated =>
            val expected = Seq(
              ReactorStarted,
              ReactorScheduled,
              ReactorPreempted)
            p.trySuccess(events == expected)
          case e =>
            events += e
            if (events.size == 3) self.main.seal()
        }
      }
      system.spawn(proto.withScheduler(scheduler))
      Await.result(p.future, 10.seconds)
    }

  property("should not process any events after sealing") =
    forAllNoShrink(choose(1, 32000)) { n =>
      val total = 32000
      val p = Promise[Boolean]()
      val fail = Promise[Boolean]()
      val max = n
      val proto = Reactor[String] { self =>
        var seen = 0
        var terminated = false
        self.main.events onEvent { s =>
          if (terminated) {
            fail.success(true)
          } else {
            seen += 1
            if (seen >= max) {
              terminated = true
              self.main.seal()
            }
          }
        }
        self.sysEvents onMatch {
          case ReactorTerminated =>
            p.success(true)
        }
      }
      val ch = system.spawn(proto.withScheduler(scheduler))
      for (i <- 0 until total) ch ! "msg"
      assert(Await.result(p.future, 10.seconds))
      Thread.sleep(2)
      fail.future.value != Some(Success(true))
    }

}


object NewThreadReactorSystemCheck extends ReactorSystemCheck("NewThreadSystem") {
  val scheduler = ReactorSystem.Bundle.schedulers.newThread
}


object GlobalExecutionContextReactorSystemCheck extends ReactorSystemCheck("ECSystem") {
  val scheduler = ReactorSystem.Bundle.schedulers.globalExecutionContext
}


object DefaultSchedulerReactorSystemCheck
extends ReactorSystemCheck("DefaultSchedulerSystem") {
  val scheduler = ReactorSystem.Bundle.schedulers.default
}


object PiggybackReactorSystemCheck extends BaseReactorSystemCheck("PiggybackSystem") {
  val scheduler = ReactorSystem.Bundle.schedulers.piggyback
}


class ReactorSystemTest extends FunSuite with Matchers {
  test("system should return without throwing") {
    val system = ReactorSystem.default("test")
    try {
      val proto = Reactor[Unit] { self => }
      system.spawn(proto)
      assert(system.frames.forName("reactor-0") != null)
    } finally system.shutdown()
  }

  test("system should return without throwing and use custom name") {
    val system = ReactorSystem.default("test")
    try {
      val proto = Reactor[Unit] { self => }
      system.spawn(proto.withName("Izzy"))
      assert(system.frames.forName("Izzy") != null)
      assert(system.frames.forName("Izzy").name == "Izzy")
    } finally system.shutdown()
  }

  test("system should throw when attempting to reuse the same name") {
    val system = ReactorSystem.default("test")
    try {
      val proto = Reactor[Unit] { self => }
      system.spawn(proto.withName("Izzy"))
      intercept[IllegalArgumentException] {
        val proto = Reactor[Unit] { self => }
        system.spawn(proto.withName("Izzy"))
      }
    } finally system.shutdown()
  }

  test("system should create a default channel for the reactor") {
    val system = ReactorSystem.default("test")
    try {
      val proto = Reactor[Unit] { self => }
      val channel = system.spawn(proto.withName("Izzy"))
      assert(channel != null)
      val conn = system.frames.forName("Izzy").connectors.forName("main")
      assert(conn != null)
      assert(conn.channel eq channel)
      assert(!conn.isDaemon)
    } finally system.shutdown()
  }

  test("system should create a system channel for the reactor") {
    val system = ReactorSystem.default("test")
    try {
      val proto = Reactor[Unit] { self => }
      val channel = system.spawn(proto.withName("Izzy"))
      val conn = system.frames.forName("Izzy").connectors.forName("system")
      assert(conn != null)
      assert(conn.isDaemon)
    } finally system.shutdown()
  }

  test("system should schedule reactor's ctor for execution") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Unit]()
      system.spawn(Proto[PromiseReactor](p))
      Await.result(p.future, 10.seconds)
    } finally system.shutdown()
  }

  test("system should invoke the ctor with the Reactor.self set") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Boolean]()
      system.spawn(Proto[ReactorSelfReactor](p))
      assert(Await.result(p.future, 10.seconds))
    } finally system.shutdown()
  }

  test("reactor should ensure the ReactorStarted event") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Boolean]()
      system.spawn(Proto[ReactorStartedReactor](p))
      assert(Await.result(p.future, 10.seconds))
    } finally system.shutdown()
  }

  test("reactor should process an event that arrives after the first batch") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Boolean]()
      val ch = system.spawn(Proto[AfterFirstBatchReactor](p))
      Thread.sleep(250)
      ch ! "success"
      assert(Await.result(p.future, 10.seconds))
    } finally system.shutdown()
  }

  test("reactor should process an event that arrives during the first batch") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Boolean]()
      val ch = system.spawn(Proto[DuringFirstBatchReactor](p))
      assert(Await.result(p.future, 10.seconds))
    } finally system.shutdown()
  }

  test("reactor should process an event that arrives during the first event") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Boolean]()
      val ch = system.spawn(Proto[DuringFirstEventReactor](p))
      ch ! "message"
      assert(Await.result(p.future, 10.seconds))
    } finally system.shutdown()
  }

  test("reactor should process two events that arrive during the first event") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Boolean]()
      val ch = system.spawn(Proto[TwoDuringFirstReactor](p))
      ch ! "start"
      assert(Await.result(p.future, 10.seconds))
    } finally system.shutdown()
  }

  test("reactor should process 100 incoming events") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Boolean]()
      val ch = system.spawn(Proto[CountdownPromiseReactor](p, 100))
      Thread.sleep(250)
      for (i <- 0 until 100) ch ! "dec"
      assert(Await.result(p.future, 10.seconds))
    } finally system.shutdown()
  }

  test("reactor should terminate after sealing its channel") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Boolean]()
      val ch = system.spawn(Proto[AfterSealTerminateReactor](p))
      ch ! "seal"
      assert(Await.result(p.future, 10.seconds))
    } finally system.shutdown()
  }

  test("reactor should be able to open a new channel") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Boolean]()
      system.spawn(Proto[NewChannelReactor](p))
      assert(Await.result(p.future, 10.seconds))
    } finally system.shutdown()
  }

  test("reactor should get ReactorScheduled events") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Boolean]()
      val ch = system.spawn(Proto[ReactorScheduledReactor](p))
      for (i <- 0 until 5) {
        Thread.sleep(60)
        ch ! "dummy"
      }
      assert(Await.result(p.future, 10.seconds))
    } finally system.shutdown()
  }

  test("reactor should get ReactorPreempted events") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Boolean]()
      system.spawn(Proto[ReactorPreemptedReactor](p))
      assert(Await.result(p.future, 10.seconds))
    } finally system.shutdown()
  }

  test("reactor should terminate on ctor exception") {
    val scheduler = new Scheduler.Dedicated.NewThread(true, Scheduler.silentHandler)
    val bundle = ReactorSystem.Bundle.default(scheduler)
    val system = ReactorSystem.default("test", bundle)
    try {
      val p = Promise[(Boolean, Boolean)]()
      system.spawn(Proto[CtorExceptionReactor](p))
      assert(Await.result(p.future, 10.seconds) == (true, true))
    } finally system.shutdown()
  }

  test("reactor does not raise ReactorDied events after ReactorTerminated") {
    val scheduler = new Scheduler.Dedicated.NewThread(true, Scheduler.silentHandler)
    val bundle = ReactorSystem.Bundle.default(scheduler)
    val system = ReactorSystem.default("test", bundle)
    try {
      val p = Promise[Boolean]()
      system.spawn(Proto[TerminationExceptionReactor](p))
      Thread.sleep(100)
      assert(p.future.value == None)
    } finally system.shutdown()
  }

  test("reactor should terminate on exceptions while running") {
    val scheduler = new Scheduler.Dedicated.NewThread(true, Scheduler.silentHandler)
    val bundle = ReactorSystem.Bundle.default(scheduler)
    val system = ReactorSystem.default("test", bundle)
    try {
      val p = Promise[Throwable]()
      val ch = system.spawn(Proto[RunningExceptionReactor](p))
      ch ! "die"
      assert(Await.result(p.future, 10.seconds).getMessage == "exception thrown")
    } finally system.shutdown()
  }

  test("Reactor.self should be correctly set") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Boolean]()
      system.spawn(Proto[SelfReactor](p))
      assert(Await.result(p.future, 10.seconds))
    } finally system.shutdown()
  }

  test("piggyback scheduler should throw an exception if called from a reactor") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Boolean]()
      system.spawn(Proto[PiggyReactor](p))
      assert(Await.result(p.future, 10.seconds))
    } finally system.shutdown()
  }

  test("after termination and before ReactorTerminated reactor name must be released") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Boolean]()
      system.spawn(Proto[TerminatedReactor](p).withName("ephemo"))
      assert(Await.result(p.future, 10.seconds))
      Thread.sleep(1200)
      assert(system.frames.forName("ephemo") == null)
    } finally system.shutdown()
  }

  test("after the reactor starts, its channel should be looked up") {
    val system = ReactorSystem.default("test")
    try {
      val started = Promise[Boolean]()
      val ended = Promise[Boolean]()
      val channel = system.spawn(Proto[LookupChannelReactor](started, ended)
        .withName("pi"))
      assert(Await.result(started.future, 10.seconds))
      system.channels.find[String]("pi#terminator") match {
        case Some(ch) => ch ! "end"
        case None => sys.error("channel not found")
      }
      assert(Await.result(ended.future, 10.seconds))
    } finally system.shutdown()
  }

  test("channels reactor should look up channels when asked") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Boolean]
      system.spawn(Proto[ChannelsAskReactor](p).withName("chaki"))
      assert(Await.result(p.future, 10.seconds))
    } finally system.shutdown()
  }
}
