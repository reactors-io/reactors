package io.reactors
package protocol



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
            if (link.trySend(i.toString)) {
              traverse(i + 1)
            } else {
              link.available.once.on(traverse(i))
            }
          }
        }
        traverse(0)
      }
    }
    assert(Await.result(done.future, 10.seconds) == Seq("0", "1", "2", "3", "4"))
  }
}
