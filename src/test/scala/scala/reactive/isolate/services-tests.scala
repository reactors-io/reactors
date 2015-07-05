package scala.reactive
package isolate



import java.io.InputStream
import java.net.URL
import org.apache.commons.io._
import org.scalatest._
import org.scalatest.Matchers
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Failure



// class NetTest extends FunSuite with Matchers {

//   val system = IsoSystem.default("TestSystem")

//   test("Resource string should be resolved") {
//     val res = Promise[String]()
//     val resolver = (url: URL) => IOUtils.toInputStream("ok", "UTF-8")
//     system.isolate(Proto[ResourceStringIso](res, resolver)
//       .withScheduler(IsoSystem.Bundle.schedulers.piggyback))
//     assert(res.future.value.get.get == "ok", s"got ${res.future.value}")
//   }

//   test("Resource string should throw an exception") {
//     val testError = new Exception
//     val res = Promise[String]()
//     val resolver: URL => InputStream = url => throw testError
//     system.isolate(Proto[ResourceStringIso](res, resolver)
//       .withScheduler(IsoSystem.Bundle.schedulers.piggyback))
//     assert(res.future.value.get == Failure(testError), s"got ${res.future.value}")
//   }

// }


// class ResourceStringIso(val res: Promise[String], val resolver: URL => InputStream)
// extends Iso[Unit] {
//   import implicits.canLeak
//   val net = new Services.Net(system, resolver)
//   val response = net.resource.string("http://dummy.url/resource.txt")
//   response onEvent { s =>
//     res success s
//     channel.seal()
//   }
//   response onExcept { case t =>
//     res failure t
//     channel.seal()
//   }
// }


// class ClockTest extends FunSuite with Matchers {

//   val system = IsoSystem.default("TestSystem")

//   test("Periodical timer should fire 3 times") {
//     system.isolate(Proto[PeriodIso].withScheduler(
//       IsoSystem.Bundle.schedulers.piggyback))
//   }

//   test("Timeout should fire exactly once") {
//     val timeoutCount = Promise[Int]()
//     system.isolate(Proto[TimeoutIso](timeoutCount).withScheduler(
//       IsoSystem.Bundle.schedulers.piggyback))
//     assert(timeoutCount.future.value.get.get == 1,
//       s"Total timeouts: ${timeoutCount.future.value}")
//   }

// }


// class PeriodIso extends Iso[Unit] {
//   import implicits.canLeak
//   var countdown = 3
//   system.clock.period(50.millis) on {
//     countdown -= 1
//     if (countdown <= 0) channel.seal()
//   }
// }


// class TimeoutIso(val timeoutCount: Promise[Int]) extends Iso[Unit] {
//   import implicits.canLeak
//   var timeouts = 0
//   system.clock.timeout(50.millis) on {
//     timeouts += 1
//     system.clock.timeout(500.millis) on {
//       channel.seal()
//       timeoutCount success timeouts
//     }
//   }
// }
