package io.reactors
package protocol



import org.scalatest._
import scala.concurrent._
import scala.concurrent.duration._



class ServerProtocolsSpec extends FunSuite {

  val system = ReactorSystem.default("server-protocols")

  test("request a reply from the server") {
    val p = Promise[Int]()
    val proto = Reactor[String] { self =>
      val server = system.server[String, Int]("length-server")
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

}
