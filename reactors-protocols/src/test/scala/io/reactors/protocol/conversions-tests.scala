package io.reactors
package protocol



import io.reactors.test._
import org.scalacheck._
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Gen.choose
import org.scalatest._
import scala.collection._
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._



class ConversionsSpec extends FunSuite {
  val system = ReactorSystem.default("server-protocols")

  test("Traversable#toEvents") {
    val events = Seq(1, 2, 3, 4).toEvents
    val buffer = mutable.Buffer[Int]()
    events.onEvent(buffer += _)
    assert(buffer == Seq(1, 2, 3, 4))
  }
}
