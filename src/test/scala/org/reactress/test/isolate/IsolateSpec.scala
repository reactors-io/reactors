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
      react <<= src.onEvent { v =>
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
      react <<= history.onEvent(x => if (x.size == many) sv.put(x))
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
        react <<= src.onEvent { _ =>
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

}


class ExecutorSyncedIsolateSpec extends IsolateSpec {

  implicit val scheduler = Scheduler.default

}


class NewThreadSyncedIsolateSpec extends IsolateSpec {

  implicit val scheduler = Scheduler.newThread

}











