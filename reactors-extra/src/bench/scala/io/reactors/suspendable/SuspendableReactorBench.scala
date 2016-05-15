package io.reactors
package suspendable


import akka.actor._
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic._
import org.scalameter.api._
import org.scalameter.japi.JBench
import scala.collection._
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success
import scala.util.Failure



class SuspendableReactorBench extends JBench.OfflineReport {
  override def defaultConfig = Context(
    exec.minWarmupRuns -> 800,
    exec.maxWarmupRuns -> 1200,
    exec.benchRuns -> 36,
    exec.independentSamples -> 4,
    verbose -> true
  )

  val sizes = Gen.range("size")(5000, 25000, 5000)

  @transient lazy val system = new ReactorSystem("reactor-bench")

  @gen("sizes")
  @benchmark("io.reactors.suspendable.ping-pong")
  @curve("suspendable")
  def reactorSuspendablePingPong(sz: Int) = {
    val done = Promise[Boolean]()

    class PingPong {
      val ping: Channel[String] = system.spawn(Reactor.suspendable {
        (self: Reactor[String]) =>
        val pong = system.spawn(Reactor.suspendable {
          (self: Reactor[String]) =>
          var left = sz
          while (left > 0) {
            val x = self.main.events.receive()
            ping ! "pong"
            left -= 1
          }
          self.main.seal()
        })
        var left = sz
        while (left > 0) {
          pong ! "ping"
          val x = self.main.events.receive()
          left -= 1
        }
        done.success(true)
        self.main.seal()
      })
    }
    new PingPong

    assert(Await.result(done.future, 10.seconds))
  }

  @gen("sizes")
  @benchmark("io.reactors.suspendable.ping-pong")
  @curve("onEvent")
  def reactorOnEventPingPong(sz: Int) = {
    val done = Promise[Boolean]()

    class PingPong {
      val ping: Channel[String] = system.spawn(Reactor { (self: Reactor[String]) =>
        val pong = system.spawn(Reactor { (self: Reactor[String]) =>
          var left = sz
          self.main.events onEvent { x =>
            ping ! "pong"
            left -= 1
            if (left == 0) self.main.seal()
          }
        })
        var left = sz
        pong ! "ping"
        self.main.events onEvent { x =>
          left -= 1
          if (left > 0) {
            pong ! "ping"
          } else {
            done.success(true)
            self.main.seal()
          }
        }
      })
    }
    new PingPong

    assert(Await.result(done.future, 10.seconds))
  }

  var actorSystem: ActorSystem = _

  def akkaPingPongSetup() {
    actorSystem = ActorSystem("actor-bench")
  }

  def akkaPingPongTeardown() {
    actorSystem.shutdown()
  }

  @gen("sizes")
  @benchmark("io.reactors.suspendable.ping-pong")
  @curve("akka")
  @setupBeforeAll("akkaPingPongSetup")
  @teardownAfterAll("akkaPingPongTeardown")
  def akkaPingPong(sz: Int) = {
    val done = Promise[Boolean]()
    val pong = actorSystem.actorOf(
      Props.create(classOf[SuspendableReactorBench.Pong], new Integer(sz)))
    val ping = actorSystem.actorOf(
      Props.create(classOf[SuspendableReactorBench.Ping], pong, new Integer(sz), done))

    assert(Await.result(done.future, 10.seconds))
  }
}


object SuspendableReactorBench {
  class Pong(val sz: Integer) extends Actor {
    var left = sz.intValue
    def receive = {
      case _ =>
        left -= 1
        sender ! "pong"
        if (left == 0) context.stop(self)
    }
  }

  class Ping(val pong: ActorRef, val sz: Integer, val done: Promise[Boolean])
  extends Actor {
    var left = sz.intValue
    pong ! "ping"
    def receive = {
      case _ =>
        left -= 1
        if (left > 0) {
          sender ! "ping"
        } else {
          done.success(true)
          context.stop(self)
        }
    }
  }
}
