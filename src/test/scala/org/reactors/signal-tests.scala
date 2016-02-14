package org.reactors



import org.scalacheck._
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Gen.choose
import org.scalatest._
import org.reactors.test._
import scala.collection._



class SignalSpec extends FunSuite {

  class TestEmitter[T] extends Events.Emitter[T] {
    var unsubscriptionCount = 0
    override def onReaction(obs: Observer[T]) = new Subscription.Composite(
      super.onReaction(obs),
      new Subscription {
        def unsubscribe() = unsubscriptionCount += 1
      }
    )
  }

  test("changes") {
    val buffer = mutable.Buffer[Int]()
    val emitter = new Events.Emitter[Int]
    emitter.toSignal(0).changes.onEvent(buffer += _)

    emitter.react(3)
    emitter.react(3)
    emitter.react(5)
    emitter.react(7)
    emitter.react(7)
    emitter.react(11)
    assert(buffer == Seq(3, 5, 7, 11))
  }

  test("diffPast") {
    val buffer = mutable.Buffer[Int]()
    val emitter = new Events.Emitter[Int]
    emitter.toSignal(0).diffPast(_ - _).onEvent(buffer += _)

    emitter.react(3)
    assert(buffer == Seq(3))
    emitter.react(3)
    assert(buffer == Seq(3, 0))
    emitter.react(5)
    assert(buffer == Seq(3, 0, 2))
    emitter.react(11)
    assert(buffer == Seq(3, 0, 2, 6))
    emitter.react(19)
    assert(buffer == Seq(3, 0, 2, 6, 8))
    emitter.unreact()
  }

  test("zip") {
    var done = false
    val buffer = mutable.Buffer[Int]()
    val e0 = new Events.Emitter[Int]
    val e1 = new Events.Emitter[Int]
    val zip = (e0.toSignal(0) zip e1.toSignal(0))(_ + _)
    zip.onEvent(buffer += _)
    zip.onDone(done = true)

    // e0.react(3)
    // assert(buffer == Seq(3))
    // e1.react(5)
    // assert(buffer == Seq(3, 8))
    // e1.react(7)
    // assert(buffer == Seq(3, 8, 10))
    // e1.react(11)
    // assert(buffer == Seq(3, 8, 10, 14))
    // e0.react(19)
    // assert(buffer == Seq(3, 8, 10, 14, 30))
    // assert(!done)
    // e1.unreact()
    // assert(buffer == Seq(3, 8, 10, 14, 30))
    // assert(done)
    // e0.react(23)
    // assert(buffer == Seq(3, 8, 10, 14, 30))
  }

}
