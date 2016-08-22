package io.reactors
package protocol



import io.reactors.test._
import org.scalacheck._
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Gen.choose
import org.scalatest._
import scala.collection._
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global



class ConversionsSpec extends FunSuite {
  val system = ReactorSystem.default("conversions")

  test("Traversable#toEvents") {
    val events = Seq(1, 2, 3, 4).toEvents
    val buffer = mutable.Buffer[Int]()
    events.onEvent(buffer += _)
    assert(buffer == Seq(1, 2, 3, 4))
  }

  test("Future#toIVar") {
    val future = Future { 7 }
    val done = Promise[Boolean]()
    system.spawn(Reactor[String] { self =>
      future.toIVar on {
        done.success(true)
        self.main.seal()
      }
    })
    assert(Await.result(done.future, 10.seconds))
  }
}
