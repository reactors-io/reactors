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

    val i = scheduler.schedule[String] { src =>
      src.onValue { v =>
        log(s"got event '$v'")
        sv.put(v)
      }
    }
    val emitter = new Reactive.Emitter[String]
    val sub = i.bind(emitter)
    emitter += "test event"

    sv.take() should equal ("test event")
  }

  it should "react to many events" in {
    val many = 50
    val sv = new SyncVar[List[Int]]

    val i = scheduler.schedule[Int] { src =>
      val history = src.scanPast(List[Int]()) {
        (acc, x) => x :: acc
      }
      history.onValue(x => if (x.size == many) sv.put(x))
    }
    val emitter = new Reactive.Emitter[Int]
    val sub = i.bind(emitter)
    for (i <- 0 until 50) emitter += i

    val expected = (0 until 50).reverse
    assert(sv.get == expected, "${sv.get} vs $expected")
  }

  it should "see itself as an isolate" in {
    val sv = new SyncVar[Boolean]

    object Container {
      val i: Isolate[Int] = scheduler.schedule[Int] { src =>
        src.onValue { _ =>
          sv.put(Isolate.self == i)
        }
      }
    }

    val emitter = new Reactive.Emitter[Int]
    val sub = Container.i.bind(emitter)
    emitter += 7

    sv.get should equal (true)
  }

}


class ExecutorSyncedIsolateSpec extends IsolateSpec {

  implicit val scheduler = Scheduler.default

}


class NewThreadSyncedIsolateSpec extends IsolateSpec {

  implicit val scheduler = Scheduler.default

}











