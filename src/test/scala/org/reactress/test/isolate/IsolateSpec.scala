package org.reactress
package test.isolate



import scala.collection._
import scala.concurrent._
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



trait IsolateSpec extends FlatSpec with ShouldMatchers {

  implicit val scheduler: Scheduler

  def log(msg: String) = println(s"${Thread.currentThread.getName}: $msg")

  "A synced isolate" should "react to a message" in {
    val sv = new SyncVar[String]

    val i = scheduler.schedule[String] { reactive =>
      reactive.onValue { v =>
        log(s"got event '$v'")
        sv.put(v)
      }
    }
    val emitter = new Reactive.Emitter[String]
    i.bind(emitter)
    emitter += "test event"

    sv.take() should equal ("test event")
  }

}


class SyncedIsolateSpec extends IsolateSpec {
  implicit val scheduler = org.reactress.isolate.default
}










