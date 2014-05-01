package org.reactress
package test.isolate



import scala.collection._
import scala.concurrent._
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



trait Logging {
  def log(msg: String) = println(s"${Thread.currentThread.getName}: $msg")
}


trait IsolateSpec extends FlatSpec with ShouldMatchers with Logging {

  val isoSystem: IsolateSystem

  implicit val scheduler: Scheduler

  "A synced isolate" should "react to an event" in {
    val sv = new SyncVar[String]

    class OneIso extends Isolate[String] {
      react <<= source.onEvent { v =>
        log(s"got event '$v'")
        sv.put(v)
      }
    }

    val emitter = new Reactive.Emitter[String]
    val c = isoSystem.isolate[String, String, OneIso](Proto[OneIso]).attach(emitter).seal()
    emitter += "test event"
    emitter.close()

    sv.take() should equal ("test event")
  }

  it should "react to many events" in {
    val many = 50
    val sv = new SyncVar[List[Int]]

    class ManyIso extends Isolate[Int] {
      val history = source.scanPast(List[Int]()) {
        (acc, x) => x :: acc
      }
      react <<= history.onEvent {
        x => if (x.size == many) sv.put(x)
      }
    }

    val emitter = new Reactive.Emitter[Int]
    val c = isoSystem.isolate[Int, Int, ManyIso](Proto[ManyIso]).attach(emitter).seal()
    for (i <- 0 until 50) emitter += i
    emitter.close()

    val expected = (0 until 50).reverse
    assert(sv.get == expected, "${sv.get} vs $expected")
  }

  it should "see itself as an isolate" in {
    val sv = new SyncVar[Boolean]

    val emitter = new Reactive.Emitter[Int]

    class SelfIso extends Isolate[Int] {
      react <<= source.onEvent { _ =>
        log(s"${ReactIsolate.self} vs ${this}}")
        sv.put(ReactIsolate.self[SelfIso] == this)
      }
    }

    val c = isoSystem.isolate[Int, Int, SelfIso](Proto[SelfIso]).attach(emitter).seal()

    emitter += 7
    emitter.close()

    sv.get should equal (true)
  }

}


trait LooperIsolateSpec extends FlatSpec with ShouldMatchers with Logging {

  val isoSystem: IsolateSystem

  implicit val scheduler: Scheduler

  "A LooperIsolate" should "do 3 loops" in {
    val sv = new SyncVar[Int]

    println("looper -----------")

    class TestLooper extends Isolate.Looper[Int] {
      val fallback = ReactCell(Option(1))

      react <<= source.scanPast(0)(_ + _) onEvent { e =>
        log(s"scanned to $e")
        if (e >= 3) {
          sv.put(e)
          fallback := None
        }
      }
    }

    isoSystem.isolate[Int, Int, TestLooper](Proto[TestLooper])

    sv.get should equal (3)
  }
}


class ExecutorSyncedIsolateSpec extends IsolateSpec with LooperIsolateSpec {

  val isoSystem = IsolateSystem.default("TestSystem")

  implicit val scheduler = Scheduler.default

}


class NewThreadSyncedIsolateSpec extends IsolateSpec with LooperIsolateSpec {

  val isoSystem = IsolateSystem.default("TestSystem")

  implicit val scheduler = Scheduler.newThread

}


class PiggybackSyncedIsolateSpec extends LooperIsolateSpec {

  val isoSystem = IsolateSystem.default("TestSystem")

  implicit val scheduler = Scheduler.piggyback

}











