package io.reactors.protocol
package instrumented



import io.reactors.ReactorSystem
import io.reactors.ReactorSystem.Bundle
import io.reactors.ReactorTerminated
import org.scalatest._
import org.scalatest.concurrent.AsyncTimeLimitedTests
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise
import scala.concurrent.duration._



class ScriptedTransportTests extends AsyncFunSuite with AsyncTimeLimitedTests {
  val system = ReactorSystem.default("conversions", Bundle.default(
    """
    system.channels.create-as-local = "false"
    """.stripMargin))

  def timeLimit = 10.seconds

  implicit override def executionContext = ExecutionContext.Implicits.global

  test("use a scripted transport") {
    val done = Promise[Boolean]

    system.spawnLocal[Unit] { self =>
      // TODO: Use a scenario that relies on scripted testing.
      done.success(true)
    }

    done.future.map(t => assert(t))
  }
}
