package io.reactors.protocol



import io.reactors.ReactorSystem
import org.scalatest._
import org.scalatest.concurrent.AsyncTimeLimitedTests
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise
import scala.concurrent.duration._



class ReliableProtocolsSpec extends AsyncFunSuite with AsyncTimeLimitedTests {
  val system = ReactorSystem.default("conversions")

  def timeLimit = 10.seconds

  implicit override def executionContext = ExecutionContext.Implicits.global

  test("open a reliable channel and receive an event") {
    val done = Promise[Boolean]

    system.spawnLocal[Unit] { self =>
      val server = system.channels.daemon.reliableServer[String]
        .serveReliable(Reliable.Policy.ordered(128))

      server.connections onEvent { connection =>
        connection.events onMatch {
          case "finish" =>
            done.success(true)
            self.main.seal()
        }
      }

      server.channel.openReliable(Reliable.Policy.ordered(128)) onEvent { reliable =>
        reliable.channel ! "finish"
      }
    }

    done.future.map(t => assert(t))
  }
}
