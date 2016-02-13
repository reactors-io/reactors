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

}
