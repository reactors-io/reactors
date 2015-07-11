package scala.reactive



import org.scalatest.{FunSuite, Matchers}
import scala.concurrent._
import scala.concurrent.duration._



class IsoSystemTest extends FunSuite with Matchers {

  class TestIsoSystem extends IsoSystem {
    def uniqueId() = ???
    def uniqueName(name: String) = ???
    def releaseNames(name: String) = ???
    def newChannel[@spec(Int, Long, Double) Q](reactor: Reactor[Q]): Channel[Q] = ???
    def name = "TestIsoSystem"
    def bundle = IsoSystem.defaultBundle
    def channels = ???
  }

  test("system should return without throwing") {
    val system = new TestIsoSystem
    val proto = Proto[IsoSystemTest.TestIso]
    system.isolate(proto)
    assert(system.frames.forName("isolate-0") != null)
  }

  test("system should return without throwing and use custom name") {
    val system = new TestIsoSystem
    val proto = Proto[IsoSystemTest.TestIso].withName("Izzy")
    system.isolate(proto)
    assert(system.frames.forName("Izzy") != null)
    assert(system.frames.forName("Izzy").name == "Izzy")
  }

  test("system should throw when attempting to reuse the same name") {
    val system = new TestIsoSystem
    system.isolate(Proto[IsoSystemTest.TestIso].withName("Izzy"))
    intercept[IllegalArgumentException] {
      system.isolate(Proto[IsoSystemTest.TestIso].withName("Izzy"))
    }
  }

  test("system should create a default channel for the isolate") {
    val system = new TestIsoSystem
    val channel = system.isolate(Proto[IsoSystemTest.TestIso].withName("Izzy"))
    assert(channel != null)
    val conn = system.frames.forName("Izzy").connectors.forName("default")
    assert(conn != null)
    assert(conn.channel eq channel)
    assert(!conn.isDaemon)
  }

  test("system should create a system channel for the isolate") {
    val system = new TestIsoSystem
    system.isolate(Proto[IsoSystemTest.TestIso].withName("Izzy"))
    val conn = system.frames.forName("Izzy").connectors.forName("system")
    assert(conn != null)
    assert(conn.isDaemon)
  }

  test("system should schedule isolate's ctor for execution") {
    val system = new TestIsoSystem
    val p = Promise[Unit]()
    system.isolate(Proto[IsoSystemTest.PromiseIso](p))
    Await.result(p.future, 5.seconds)
  }

  test("system should invoke the ctor with the Iso.self set") {
    val system = new TestIsoSystem
    val p = Promise[Boolean]()
    system.isolate(Proto[IsoSystemTest.IsoSelfIso](p))
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should ensure the IsoStarted event") {
    val system = new TestIsoSystem
    val p = Promise[Boolean]()
    system.isolate(Proto[IsoSystemTest.IsoStartedIso](p))
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should process an event that arrives after the first batch") {
    val system = new TestIsoSystem
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[IsoSystemTest.AfterFirstBatchIso](p))
    Thread.sleep(250)
    ch ! "success"
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should process an event that arrives during the first batch") {
    val system = new TestIsoSystem
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[IsoSystemTest.DuringFirstBatchIso](p))
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should process an event that arrives during the first event") {
    val system = new TestIsoSystem
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[IsoSystemTest.DuringFirstEventIso](p))
    ch ! "message"
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should process two events that arrive during the first event") {
    val system = new TestIsoSystem
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[IsoSystemTest.TwoDuringFirstIso](p))
    ch ! "start"
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should process 100 incoming events") {
    val system = new TestIsoSystem
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[IsoSystemTest.CountdownIso](p, 100))
    Thread.sleep(250)
    for (i <- 0 until 100) ch ! "dec"
    assert(Await.result(p.future, 5.seconds))
  }

  test("iso should terminate after sealing its channel") {
    val system = new TestIsoSystem
    val p = Promise[Boolean]()
    val ch = system.isolate(Proto[IsoSystemTest.AfterSealTerminateIso](p))
    ch ! "seal"
    assert(Await.result(p.future, 5.seconds))
  }

}


object IsoSystemTest {

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
      case "seal" => connector.seal()
    }
    sysEvents onCase {
      case IsoTerminated => p.success(true)
    }
  }

}
