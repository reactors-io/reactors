package org.reactress
package test.isolate



import scala.collection._
import scala.concurrent._
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



trait IsolateSpec extends FlatSpec with ShouldMatchers {

  implicit val scheduler: Scheduler

  def log(msg: String) = println(s"${Thread.currentThread.getName}: $msg")

  "A synced isolate" should "react to an event" in {
    val sv = new SyncVar[String]

    class Iso(src: Reactive[String]) extends Isolate[String] {
      react <<= src.onEvent { case v =>
        log(s"got event '$v'")
        sv.put(v)
      }
    }

    val emitter = new Reactive.Emitter[String]
    val i = scheduler.schedule(Reactive.single(emitter))(new Iso(_))
    emitter += "test event"
    emitter.close()

    sv.take() should equal ("test event")
  }

  it should "react to many events" in {
    val many = 50
    val sv = new SyncVar[List[Int]]

    class Iso(src: Reactive[Int]) extends Isolate[Int] {
      val history = src.scanPast(List[Int]()) {
        (acc, x) => x :: acc
      }
      react <<= history.onEvent {
        case x => if (x.size == many) sv.put(x)
      }
    }

    val emitter = new Reactive.Emitter[Int]
    val i = scheduler.schedule(Reactive.single(emitter))(new Iso(_))
    for (i <- 0 until 50) emitter += i
    emitter.close()

    val expected = (0 until 50).reverse
    assert(sv.get == expected, "${sv.get} vs $expected")
  }

  it should "see itself as an isolate" in {
    val sv = new SyncVar[Boolean]

    val emitter = new Reactive.Emitter[Int]
    object Container {
      class Iso(src: Reactive[Int]) extends Isolate[Int] {
        react <<= src.onEvent { case _ =>
          log(s"${Isolate.self} vs $i}")
          sv.put(Isolate.self == i)
        }
      }

      val i: Isolate[Int] = scheduler.schedule(Reactive.single(emitter))(new Iso(_))
    }
    Container

    emitter += 7
    emitter.close()

    sv.get should equal (true)
  }

  it should "get an empty queue system event" in {
    val sv = new SyncVar[Boolean]

    val emitter = new Reactive.Emitter[Int]

    class Iso(src: Reactive[Int]) extends Isolate[Int] {
      react <<= sysEvents.onEvent {
        case Isolate.EmptyEventQueue =>
          log("empty event queue!")
          sv.put(true)
      }
      react <<= src.onEvent {
        case e => log(s"got event '$e'")
      }
    }

    val i = scheduler.schedule(Reactive.single(emitter))(new Iso(_))

    emitter += 11
    emitter.close()

    sv.get should equal (true)
  }

}


trait LooperIsolateSpec extends FlatSpec with ShouldMatchers {
  implicit val scheduler: Scheduler

  "A LooperIsolate" should "do 3 loops" in {
    val sv = new SyncVar[Int]

    Isolate.looper(1) { src =>
      Isolate.self.react <<= src.scanPast(0)(_ + _) onEvent { case e =>
        if (e >= 3) {
          sv.put(e)
          Isolate.self match {
            case i: Isolate.Looper[_] => i.stop()
          }
        }
      }
    }

    sv.get should equal (3)
  }
}


class ExecutorSyncedIsolateSpec extends IsolateSpec with LooperIsolateSpec {

  implicit val scheduler = Scheduler.default

}


class NewThreadSyncedIsolateSpec extends IsolateSpec with LooperIsolateSpec {

  implicit val scheduler = Scheduler.newThread

}


class PiggybackSyncedIsolateSpec extends LooperIsolateSpec {

  implicit val scheduler = Scheduler.piggyback

}











