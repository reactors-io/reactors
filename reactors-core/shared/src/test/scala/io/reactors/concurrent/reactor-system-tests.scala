package io.reactors
package concurrent



import io.reactors.test._
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


class ReactorSystemTest extends FunSuite with Matchers {
  test("system should return without throwing") {
    val system = ReactorSystem.default("test")
    try {
      val proto = Reactor[Unit] { self => }
      system.spawn(proto)
      assert(system.frames.forName("reactor-1") != null)
    } finally system.shutdown()
  }

  test("system should return without throwing and use custom name") {
    val system = ReactorSystem.default("test")
    try {
      val proto = Reactor[Unit] { self => }
      system.spawn(proto.withName("Izzy"))
      assert(system.frames.forName("Izzy") != null)
      assert(system.frames.forName("Izzy").frame.name == "Izzy")
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
      val conn =
        system.frames.forName("Izzy").connectors("main").asInstanceOf[Connector[_]]
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
      val conn =
        system.frames.forName("Izzy").connectors("system").asInstanceOf[Connector[_]]
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
      system.channels.get[String]("pi#terminator") match {
        case Some(ch) => ch ! "end"
        case None => sys.error("channel not found")
      }
      assert(Await.result(ended.future, 10.seconds))
    } finally system.shutdown()
  }

  test("channel resolution reactor should look up channels when asked") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[Boolean]
      system.spawn(Proto[ChannelsAskReactor](p).withName("chaki"))
      assert(Await.result(p.future, 10.seconds))
    } finally system.shutdown()
  }

  test("channel await reactor should await channels when asked") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[String]
      val awaitee = Reactor[String] { self =>
        self.main.events.onEvent { x =>
          p.success(x)
          self.main.seal()
        }
      }
      system.spawn(awaitee.withName("awaitee"))
      system.spawn(Reactor[String] { self =>
        val answer = system.channels.daemon.open[Channel[_]]
        system.names.await ! (("awaitee#main", answer.channel))
        answer.events onMatch {
          case (ch: Channel[String] @unchecked) =>
            ch ! "done"
            self.main.seal()
        }
      })
      assert(Await.result(p.future, 10.seconds) == "done")
    } finally system.shutdown()
  }

  test("channel await reactor should await a channel that appears later") {
    val system = ReactorSystem.default("test")
    try {
      val p = Promise[String]
      val ch = system.spawn(Reactor[String] { self =>
        val answer = system.channels.daemon.open[Channel[_]]
        system.names.await ! (("awaitee#main", answer.channel))
        answer.events onMatch {
          case (ch: Channel[String] @unchecked) =>
            ch ! "gotem"
            self.main.seal()
        }
        system.clock.timeout(1.second) on {
          val proto = Reactor[String] { self =>
            self.main.events.onEvent { x =>
              p.success(x)
              self.main.seal()
            }
          }
          system.spawn(proto.withName("awaitee"))
        }
      })
      assert(Await.result(p.future, 10.seconds) == "gotem")
      Thread.sleep(1000)
      assert(system.channels.get("awaitee#main") == None)
    } finally system.shutdown()
  }
}
