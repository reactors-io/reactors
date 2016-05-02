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



class ServerProtocolsSpec extends FunSuite {
  val system = ReactorSystem.default("server-protocols")

  test("request a reply from the server") {
    val p = Promise[Int]()
    val proto = Reactor[String] { self =>
      val server = system.channels.named("length-server").server[String, Int]
      server.events onEvent {
        case (s, ch) => ch ! s.length
      }
      self.sysEvents onMatch {
        case ReactorStarted =>
          (server.channel ? "ok") onEvent { len =>
            p.success(len)
            self.main.seal()
            server.seal()
          }
      }
    }
    system.spawn(proto)
    assert(Await.result(p.future, 10.seconds) == 2)
  }

  test("request a reply from a server reactor") {
    val p = Promise[Int]()
    val server = system.server((x: Int) => x + 17)
    val client = system.spawn(Reactor[Int] { self =>
      (server ? 11) onEvent { y =>
        p.success(y)
      }
    })
    assert(Await.result(p.future, 10.seconds) == 28)
  }

  test("request a reply from a maybe-server reactor") {
    val p = Promise[Int]()
    val failed = Promise[Boolean]()
    val server = system.maybeServer((x: Int) => x + 17, -1)
    val client = system.spawn(Reactor[Int] { self =>
      (server ? -18) on {
        failed.success(true)
      }
      Thread.sleep(50)
      (server ? 11) onEvent { y =>
        p.success(y)
        self.main.seal()
      }
    })
    assert(Await.result(p.future, 10.seconds) == 28)
    Thread.sleep(10)
    assert(failed.future.value == None)
  }
}


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
