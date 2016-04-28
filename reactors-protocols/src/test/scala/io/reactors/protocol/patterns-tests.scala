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



class PatternsSpec extends FunSuite {
  val system = ReactorSystem.default("server-protocols")

  test("throttle") {
    val a0 = Promise[Int]()
    val a1 = Promise[Int]()
    val a2 = Promise[Int]()
    val ch = system.spawn(Reactor[Int] { self =>
      var left = 3
      self.main.events.throttle(_ => 500.millis) onEvent { x =>
        left -= 1
        if (left == 2) a2.success(x)
        if (left == 1) a1.success(x)
        if (left == 0) {
          a0.success(x)
          self.main.seal()
        }
      }
    })
    ch ! 7
    ch ! 11
    ch ! 17
    Thread.sleep(200)
    assert(a2.future.value.get.get == 7)
    assert(a1.future.value == None)
    assert(a0.future.value == None)
    Thread.sleep(500)
    assert(a1.future.value.get.get == 11)
    assert(a0.future.value == None)
    Thread.sleep(500)
    assert(a0.future.value.get.get == 17)
  }
}


class PatternsCheck extends Properties("Patterns") with ExtendedProperties {
  val system = ReactorSystem.default("check-system")

  val sizes = detChoose(0, 50)

  property("throttle") = forAllNoShrink(sizes) {
    num =>
    stackTraced {
      val buffer = mutable.Buffer[Int]()
      val seen = Promise[Seq[Int]]()
      val ch = system.spawn(Reactor[Int] { self =>
        if (num == 0) seen.success(buffer)
        self.main.events.throttle(_ => 1.millis) onEvent { i =>
          buffer += i
          if (i == num - 1) {
            seen.success(buffer)
            self.main.seal()
          }
        }
      })
      for (i <- 0 until num) ch ! i
      try {
        assert(Await.result(seen.future, 10.seconds) == (0 until num))
      } catch {
        case t: Throwable =>
          println(num, buffer)
          throw t
      }
      true
    }
  }
}
