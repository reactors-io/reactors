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



class BackpressureProtocolsSpec extends FunSuite {
  val system = ReactorSystem.default("backpressure-protocols")

  test("open a per client backpressure channel and send events") {
    val done = Promise[Seq[String]]()
    val server = system.backpressurePerClient[String](3) { events =>
      val seen = mutable.Buffer[String]()
      events onEvent { s =>
        seen += s
        if (seen.length == 5) {
          done.success(seen)
          Reactor.self.main.seal()
        }
      }
    }
    system.spawnLocal[Long] { self =>
      server.link onEvent { link =>
        def traverse(i: Int) {
          if (i == 5) {
            self.main.seal()
          } else {
            if (link.trySend(i.toString)) traverse(i + 1)
            else link.available.once.on(traverse(i))
          }
        }
        traverse(0)
      }
    }
    assert(Await.result(done.future, 10.seconds) == Seq("0", "1", "2", "3", "4"))
  }

  test("open backpressure for all channel and send events") {
    val done = Promise[Seq[String]]()
    val server = system.backpressureForAll[String](3) { events =>
      val seen = mutable.Buffer[String]()
      events onEvent { s =>
        seen += s
        if (seen.length == 15) {
          done.success(seen)
          Reactor.self.main.seal()
        }
      }
    }
    for (j <- 0 until 3) system.spawnLocal[Long] { self =>
      server.link onEvent { link =>
        def traverse(i: Int) {
          if (i == 5) {
            self.main.seal()
          } else {
            val x = j * 5 + i
            if (link.trySend(x.toString)) traverse(i + 1)
            else link.available.once.on(traverse(i))
          }
        }
        traverse(0)
      }
    }
    val result = Await.result(done.future, 10.seconds).toSet
    assert(result == (0 until 15).map(_.toString).toSet)
  }
}


class BackpressureProtocolsCheck
extends Properties("BackpressureProtocolsCheck") with ExtendedProperties {
  val system = ReactorSystem.default("check-system")

  val sizes = detChoose(1, 5)

  property("backpressure for all") = forAllNoShrink(sizes) {
    num =>
    stackTraced {
      val done = Promise[Seq[String]]()
      val server = system.backpressureForAll[String](3) { events =>
        val seen = mutable.Buffer[String]()
        events onEvent { s =>
          seen += s
          if (seen.length == num * 5) {
            done.success(seen)
            Reactor.self.main.seal()
          }
        }
      }
      for (j <- 0 until num) system.spawnLocal[Long] { self =>
        server.link onEvent { link =>
          def traverse(i: Int) {
            if (i == 5) {
              self.main.seal()
            } else {
              val x = j * 5 + i
              if (link.trySend(x.toString)) traverse(i + 1)
              else link.available.once.on(traverse(i))
            }
          }
          traverse(0)
        }
      }
      val result = Await.result(done.future, 10.seconds).toSet
      result == (0 until (num * 5)).map(_.toString).toSet
    }
  }
}
