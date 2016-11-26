package io.reactors.protocol



import io.reactors.ReactorSystem.Bundle
import io.reactors._
import io.reactors.protocol.instrument.Scripted
import org.scalatest._
import org.scalatest.concurrent.AsyncTimeLimitedTests
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise
import scala.concurrent.duration._



class ReliableProtocolsSpec extends AsyncFunSuite with AsyncTimeLimitedTests {
  val system = ReactorSystem.default("conversions", Scripted.defaultBundle)

  def timeLimit = 10.seconds

  implicit override def executionContext = ExecutionContext.Implicits.global

  test("open a reliable channel and receive an event") {
    val done = Promise[Boolean]

    system.spawnLocal[Unit] { self =>
      val server = system.channels.daemon.reliableServer[String]
        .serveReliable(Reliable.Policy.reorder(128))

      server.connections onEvent { connection =>
        connection.events onMatch {
          case "finish" =>
            done.success(true)
            self.main.seal()
        }
      }

      server.channel.openReliable(Reliable.Policy.reorder(128)) onEvent { reliable =>
        reliable.channel ! "finish"
      }
    }

    done.future.map(t => assert(t))
  }

  test("restore proper order when the underlying channel reorders events") {
    val event1 = Promise[String]
    val event2 = Promise[String]

    val policy = Reliable.Policy.reorder[String](128)
    system.channels.registerTemplate(TwoWay.InputTag, system.channels.named("incoming"))

    val proto = Reactor.reliableServer(policy) {
      (server, connection) =>
      connection.events onEvent { x =>
        if (!event1.trySuccess(x)) {
          event2.success(x)
          server.subscription.unsubscribe()
        }
      }
    }
    val server = system.spawn(proto.withName("server"))

    system.spawnLocal[Unit] { self =>
      server.openReliable(policy) onEvent { r =>
        val twoWay = self.system.channels.get[Stamp[String]]("server", "incoming").get
        self.system.service[Scripted].behavior(twoWay) {
          _.take(2).reverse
        }
        r.channel ! "first"
        r.channel ! "second"
      }
    }

    val done = for {
      x <- event1.future
      y <- event2.future
    } yield (x, y)
    done.map(t => assert(t == ("first", "second")))
  }

  test("restore proper order when acknowledgements are delayed") {
    val done = Promise[Seq[Int]]()

    val total = 256
    val window = total / 2
    val policy = Reliable.Policy.reorder[Int](window)
    system.channels.registerTemplate(TwoWay.OutputTag, system.channels.named("acks"))

    val server = system.reliableServer(policy) { (server, connection) =>
      val seen = mutable.Buffer[Int]()
      connection.events onEvent { x =>
        seen += x
        if (x == total - 1) {
          done.success(seen)
        }
      }
    }

    val proto = Reactor[Unit] { self =>
      server.openReliable(policy) onEvent { r =>
        val acks = self.system.channels.get[Long]("client", "acks").get
        self.system.service[Scripted].behavior(acks) {
          _.take(window).reverse
        }
        for (i <- 0 until total) {
          r.channel ! i
        }
      }
    }
    system.spawn(proto.withName("client"))

    done.future.map(t => assert(t == (0 until total)))
  }
}
