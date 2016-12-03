package io.reactors
package protocol



import io.reactors.test._
import org.scalacheck._
import org.scalacheck.Prop.forAllNoShrink
import scala.collection._
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._



class BackpressureProtocolsCheck
extends Properties("BackpressureProtocolsCheck") with ExtendedProperties {
  val sizes = detChoose(1, 256)

  property("batching backpressure on two-way channel") = forAllNoShrink(sizes, sizes) {
    (rawTotal, window) =>
    stackTraced {
      val total = math.max(1, rawTotal)
      val done = Promise[Seq[Int]]()
      val system = ReactorSystem.default("check-system")
      val medium = Backpressure.Medium.default[Int]
      val policy = Backpressure.Policy.batching[Int](window)

      val server = system.spawnLocal[TwoWay.Req[Int, Int]] { self =>
        val pumpServer = self.main.serveBackpressure(medium, policy)
        pumpServer.connections onEvent { pump =>
          val seen = mutable.Buffer[Int]()
          pump.buffer.available.becomes(true) on {
            while (pump.buffer.available()) {
              val x = pump.buffer.dequeue()
              seen += x
              if (x == (total - 1)) {
                done.success(seen)
                pumpServer.subscription.unsubscribe()
              }
            }
          }
        }
      }

      system.spawnLocal[Unit] { self =>
        server.connectBackpressure(medium, policy) onEvent { valve =>
          var i = 0
          valve.available.becomes(true) on {
            while (valve.available() && i < total) {
              valve.channel ! i
              i += 1
            }
          }
        }
      }

      assert(Await.result(done.future, 5.seconds) == (0 until total))
      system.shutdown()
      true
    }
  }
}
