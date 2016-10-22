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



class BackpressureProtocolsCheck
extends Properties("BackpressureProtocolsCheck") with ExtendedProperties {
  val system = ReactorSystem.default("check-system")

  val sizes = detChoose(1, 5)

  // property("backpressure for all") = forAllNoShrink(sizes) {
  //   num =>
  //   stackTraced {
  //     val done = Promise[Seq[String]]()
  //     val server = system.backpressureForAll[String](3) { events =>
  //       val seen = mutable.Buffer[String]()
  //       events onEvent { s =>
  //         seen += s
  //         if (seen.length == num * 5) {
  //           done.success(seen)
  //           Reactor.self.main.seal()
  //         }
  //       }
  //     }
  //     for (j <- 0 until num) system.spawnLocal[Long] { self =>
  //       server.link onEvent { link =>
  //         def traverse(i: Int) {
  //           if (i == 5) {
  //             self.main.seal()
  //           } else {
  //             val x = j * 5 + i
  //             if (link.trySend(x.toString)) traverse(i + 1)
  //             else link.available.once.on(traverse(i))
  //           }
  //         }
  //         traverse(0)
  //       }
  //     }
  //     val result = Await.result(done.future, 10.seconds).toSet
  //     result == (0 until (num * 5)).map(_.toString).toSet
  //   }
  // }
}
