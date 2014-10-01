package scala.reactive
package test.isolate



import scala.collection._
import scala.concurrent.SyncVar
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



object Logging {
  def log(msg: String) = println(s"${Thread.currentThread.getName}: $msg")
}


object Isolates {
  import Logging._

  class OneIso(sv: SyncVar[String]) extends Iso[String] {
    react <<= events.onEvent { v =>
      log(s"got event '$v'")
      sv.put(v)
    }
  }

  class ManyIso(many: Int, sv: SyncVar[List[Int]]) extends Iso[Int] {
    val history = events.scanPast(List[Int]()) {
      (acc, x) => x :: acc
    }
    react <<= history.onEvent {
      x => if (x.size == many) sv.put(x)
    }
    //react <<= sysEvents.onEvent(println)
  }

  class SelfIso(sv: SyncVar[Boolean]) extends Iso[Int] {
    react <<= events.onEvent { _ =>
      log(s"${Iso.self} vs ${this}}")
      sv.put(Iso.self[SelfIso] == this)
    }
  }

  class TestLooper(sv: SyncVar[Int]) extends isolate.Looper[Int] {
    val fallback = RCell(Option(1))

    react <<= sysEvents onCase {
      case IsoEmptyQueue => log(s"empty queue!")
    }

    react <<= events.scanPast(0)(_ + _) onEvent { e =>
      log(s"scanned to $e")
      if (e >= 3) {
        sv.put(e)
        fallback := None
      }
    }
  }

  class CustomIso(sv: SyncVar[Boolean]) extends Iso[Int] {
    react <<= events on {
      sv.put(false)
    }

    react <<= sysEvents onCase {
      case IsoTerminated => if (!sv.isSet) sv.put(true)
    }
  }

  class AutoClosingIso(sv: SyncVar[Boolean]) extends Iso[Int] {
    val emitter = new Reactive.Emitter[Int]

    react <<= emitter onUnreact {
      sv.put(true)
    }
  }

  class DualChannelIso(sv: SyncVar[Int]) extends Iso[Channel[Channel[Int]]] {
    val second = open[Int]

    react <<= second.events onEvent { i =>
      sv.put(i)
      second.channel.seal()
    }

    react <<= events onEvent { c =>
      c << second.channel
    }
  }

  class MasterIso(ask: Channel[Channel[Channel[Int]]]) extends Iso[Channel[Int]] {
    react <<= sysEvents onCase {
      case IsoStarted => ask << channel
    }

    react <<= events onEvent { c =>
      c << 7
    }
  }

  class RegChannelIso(sv: SyncVar[Int]) extends Iso[Null] {
    val second = open[Int]
    system.channels("secondChannel") = second.channel

    react <<= second.events onEvent { i =>
      sv.put(i)
      second.channel.seal()
      system.channels.remove("secondChannel")
    }
  }

  class LookupIso extends Iso[Null] {
    react <<= system.channels.iget[Int]("secondChannel").use { c =>
      c << 7
    }
  }

}


trait IsolateSpec extends FlatSpec with ShouldMatchers {
  import Logging._
  import Isolates._

  val isoSystem: IsoSystem

  "A synced isolate" should "react to an event" in {
    val sv = new SyncVar[String]

    val emitter = new Reactive.Emitter[String]
    val proto = Proto(classOf[OneIso], sv)
    val c = isoSystem.isolate(proto).attach(emitter).seal()
    emitter += "test event"
    emitter.close()

    sv.take() should equal ("test event")
  }

  def reactToMany(many: Int) {
    val sv = new SyncVar[List[Int]]

    val emitter = new Reactive.Emitter[Int]
    val proto = Proto(classOf[ManyIso], many, sv)
    val c = isoSystem.isolate(proto).attach(emitter).seal()
    for (i <- 0 until many) emitter += i
    emitter.close()

    val expected = (0 until many).reverse
    assert(sv.get == expected, "${sv.get} vs $expected")
  }

  it should "react to many events" in {
    for (i <- 2 until 10) reactToMany(i)
    for (i <- 10 until 200 by 20) reactToMany(i)
  }

  it should "see itself as an isolate" in {
    val sv = new SyncVar[Boolean]

    val emitter = new Reactive.Emitter[Int]

    val proto = Proto(classOf[SelfIso], sv)
    val c = isoSystem.isolate(proto).attach(emitter).seal()

    emitter += 7
    emitter.close()

    sv.get should equal (true)
  }

  it should "set a custom event queue" in {
    val sv = new SyncVar[Boolean]

    val emitter = new Reactive.Emitter[Int]

    val proto = Proto(classOf[CustomIso], sv).withEventQueue(EventQueue.DevNull.factory)
    val c = isoSystem.isolate(proto).attach(emitter).seal()

    emitter += 7
    emitter.close()

    sv.get should equal (true)
  }

  it should "close its reactives when it terminates" in {
    val sv = new SyncVar[Boolean]

    val proto = Proto(classOf[AutoClosingIso], sv)
    val c = isoSystem.isolate(proto).seal()

    sv.get should equal (true)
  }

  it should "receive events from all its channels" in {
    val sv = new SyncVar[Boolean]

    val emitter = new Reactive.Emitter[Channel[Int]]

    val dc = isoSystem.isolate(Proto(classOf[DualChannelIso], sv))
    val mc = isoSystem.isolate(Proto(classOf[MasterIso], dc))

    sv.get should equal (7)

    dc.seal()
    mc.seal()
    Thread.sleep(100)
  }

  it should "use channel name resolution" in {
    val sv = new SyncVar[Boolean]

    val emitter = new Reactive.Emitter[Channel[Int]]

    val rc = isoSystem.isolate(Proto(classOf[RegChannelIso], sv))
    val lc = isoSystem.isolate(Proto(classOf[LookupIso]))

    sv.get should equal (7)

    rc.seal()
    lc.seal()
    Thread.sleep(100)
  }

  it should "use channel name resolution with ivars" in {
    val sv = new SyncVar[Boolean]

    val emitter = new Reactive.Emitter[Channel[Int]]

    val lc = isoSystem.isolate(Proto(classOf[LookupIso]))
    Thread.sleep(100)
    val rc = isoSystem.isolate(Proto(classOf[RegChannelIso], sv))

    sv.get should equal (7)

    rc.seal()
    lc.seal()
    Thread.sleep(100)
  }

}


trait LooperIsolateSpec extends FlatSpec with ShouldMatchers {
  import Logging._
  import Isolates._

  val isoSystem: IsoSystem

  "A LooperIso" should "do 3 loops" in {
    val sv = new SyncVar[Int]

    println("looper -----------")

    val proto = Proto(classOf[TestLooper], sv)
    isoSystem.isolate(proto)

    sv.get should equal (3)
  }

}


class ExecutorSyncedIsolateSpec extends IsolateSpec with LooperIsolateSpec {

  val scheduler = Scheduler.default
  val bundle = IsoSystem.Bundle.default(scheduler)
  val isoSystem = IsoSystem.default("TestSystem", bundle)

}


class NewThreadSyncedIsolateSpec extends IsolateSpec with LooperIsolateSpec {

  val scheduler = Scheduler.newThread
  val bundle = IsoSystem.Bundle.default(scheduler)
  val isoSystem = IsoSystem.default("TestSystem", bundle)

}


class PiggybackSyncedIsolateSpec extends LooperIsolateSpec {

  val scheduler = Scheduler.piggyback
  val bundle = IsoSystem.Bundle.default(scheduler)
  val isoSystem = IsoSystem.default("TestSystem", bundle)

}


class TimerSyncedIsolateSpec extends LooperIsolateSpec {

  val scheduler = new Scheduler.Timer(400)
  val bundle = IsoSystem.Bundle.default(scheduler)
  val isoSystem = IsoSystem.default("TestSystem", bundle)

}











