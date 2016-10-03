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



class ServerProtocolsCheck
extends Properties("ServerProtocols") with ExtendedProperties {
  val system = ReactorSystem.default("check-system")

  val sizes = detChoose(0, 256)

  property("should stream events from the server") = forAllNoShrink(sizes) {
    num =>
    stackTraced {
      val p = Promise[Seq[Char]]()
      val serverProto = Reactor[Server.Req[(String, Char), Char]] { self =>
        self.main.events onMatch {
          case ((s, term), ch) =>
            for (c <- s * num) ch ! c
            ch ! term
            self.main.seal()
        }
      }
      val server = system.spawn(serverProto)
      val client = Reactor[Unit] { self =>
        self.sysEvents onMatch {
          case ReactorStarted =>
            val buffer = mutable.Buffer[Char]()
            server.streaming("reactors", 0.toChar).onEventOrDone(buffer += _) {
              p.success(buffer)
              self.main.seal()
            }
        }
      }
      system.spawn(client)
      Await.result(p.future, 10.seconds) == Seq("reactors" * num).flatten
    }
  }
}
