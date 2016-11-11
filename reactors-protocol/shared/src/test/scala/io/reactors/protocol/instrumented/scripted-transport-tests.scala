package io.reactors.protocol
package instrumented



import io.reactors.Reactor
import io.reactors.ReactorSystem
import io.reactors.ReactorSystem.Bundle
import io.reactors.protocol.instrument.Scripted
import org.scalatest._
import org.scalatest.concurrent.AsyncTimeLimitedTests
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise
import scala.concurrent.duration._



class ScriptedTransportTests extends AsyncFunSuite with AsyncTimeLimitedTests {
  val system = ReactorSystem.default("conversions", Bundle.default("""
    remote = {
      transports = [
        {
          schema = "scripted"
          transport = "io.reactors.protocol.instrument.ScriptedTransport"
          host = ""
          port = 0
        }
      ]
    }
    system = {
      channels = {
        create-as-local = "false"
      }
    }
  """.stripMargin))

  def timeLimit = 10.seconds

  implicit override def executionContext = ExecutionContext.Implicits.global

  test("use a scripted transport to simulate a perfect channel") {
    val done = Promise[String]

    val proto = Reactor[Unit] { self =>
      val perfect = system.channels.daemon.open[String]
      perfect.channel ! "end"
      perfect.events onEvent { x =>
        done.success(x)
        self.main.seal()
      }
    }
    system.spawn(proto.withTransport("scripted"))

    done.future.map(s => assert(s == "end"))
  }

  test("use a scripted transport to drop the first event") {
    val done = Promise[String]

    val proto = Reactor[Unit] { self =>
      val perfect = system.channels.daemon.open[String]
      system.service[Scripted].withChannel(perfect.channel) { inputs =>
        inputs.drop(1)
      }

      perfect.channel ! "dropped"
      perfect.channel ! "preserved"
      perfect.events onEvent { x =>
        done.success(x)
        self.main.seal()
      }
    }
    system.spawn(proto.withTransport("scripted"))

    done.future.map(s => assert(s == "preserved"))
  }
}
