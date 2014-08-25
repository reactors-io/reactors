package scala.reactive
package test.isolate



import scala.collection._
import scala.concurrent._
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



object Logging {
  def log(msg: String) = println(s"${Thread.currentThread.getName}: $msg")
}


object Isolates {
  import Logging._

  class OneIso(sv: SyncVar[String]) extends Isolate[String] {
    react <<= source.onEvent { v =>
      log(s"got event '$v'")
      sv.put(v)
    }
  }

  class ManyIso(many: Int, sv: SyncVar[List[Int]]) extends Isolate[Int] {
    val history = source.scanPast(List[Int]()) {
      (acc, x) => x :: acc
    }
    react <<= history.onEvent {
      x => if (x.size == many) sv.put(x)
    }
  }

  class SelfIso(sv: SyncVar[Boolean]) extends Isolate[Int] {
    react <<= source.onEvent { _ =>
      log(s"${Isolate.self} vs ${this}}")
      sv.put(Isolate.self[SelfIso] == this)
    }
  }

  class TestLooper(sv: SyncVar[Int]) extends isolate.Looper[Int] {
    val fallback = ReactCell(Option(1))

    react <<= sysEvents onCase {
      case IsolateEmptyQueue => log(s"empty queue!")
    }

    react <<= source.scanPast(0)(_ + _) onEvent { e =>
      log(s"scanned to $e")
      if (e >= 3) {
        sv.put(e)
        fallback := None
      }
    }
  }

  class CustomIso(sv: SyncVar[Boolean]) extends Isolate[Int] {
    react <<= source on {
      sv.put(false)
    }

    react <<= sysEvents onCase {
      case IsolateTerminated => if (!sv.isSet) sv.put(true)
    }
  }

}


trait IsolateSpec extends FlatSpec with ShouldMatchers {
  import Logging._
  import Isolates._

  val isoSystem: IsolateSystem

  "A synced isolate" should "react to an event" in {
    val sv = new SyncVar[String]

    val emitter = new Reactive.Emitter[String]
    val proto = Proto(classOf[OneIso], sv)
    val c = isoSystem.isolate(proto).attach(emitter).seal()
    emitter += "test event"
    emitter.close()

    sv.take() should equal ("test event")
  }

  it should "react to many events" in {
    val many = 50
    val sv = new SyncVar[List[Int]]

    val emitter = new Reactive.Emitter[Int]
    val proto = Proto(classOf[ManyIso], many, sv)
    val c = isoSystem.isolate(proto).attach(emitter).seal()
    for (i <- 0 until 50) emitter += i
    emitter.close()

    val expected = (0 until 50).reverse
    assert(sv.get == expected, "${sv.get} vs $expected")
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

}


trait LooperIsolateSpec extends FlatSpec with ShouldMatchers {
  import Logging._
  import Isolates._

  val isoSystem: IsolateSystem

  "A LooperIsolate" should "do 3 loops" in {
    val sv = new SyncVar[Int]

    println("looper -----------")

    val proto = Proto(classOf[TestLooper], sv)
    isoSystem.isolate(proto)

    sv.get should equal (3)
  }
}


class ExecutorSyncedIsolateSpec extends IsolateSpec with LooperIsolateSpec {

  val scheduler = Scheduler.default
  val bundle = IsolateSystem.Bundle.default(scheduler)
  val isoSystem = IsolateSystem.default("TestSystem", bundle)

}


class NewThreadSyncedIsolateSpec extends IsolateSpec with LooperIsolateSpec {

  val scheduler = Scheduler.newThread
  val bundle = IsolateSystem.Bundle.default(scheduler)
  val isoSystem = IsolateSystem.default("TestSystem", bundle)

}


class PiggybackSyncedIsolateSpec extends LooperIsolateSpec {

  val scheduler = Scheduler.piggyback
  val bundle = IsolateSystem.Bundle.default(scheduler)
  val isoSystem = IsolateSystem.default("TestSystem", bundle)

}


class TimerSyncedIsolateSpec extends LooperIsolateSpec {

  val scheduler = new Scheduler.Timer(400)
  val bundle = IsolateSystem.Bundle.default(scheduler)
  val isoSystem = IsolateSystem.default("TestSystem", bundle)

}











