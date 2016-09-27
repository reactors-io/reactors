package io.reactors
package concurrent



import io.reactors.test._
import java.io.InputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import org.apache.commons.io._
import org.scalatest._
import org.scalatest.concurrent.TimeLimitedTests
import scala.collection._
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Failure



class NetTest extends FunSuite with Matchers with BeforeAndAfterAll {

  val system = ReactorSystem.default("TestSystem")

  test("resource string should be resolved") {
    val res = Promise[String]()
    val resolver = (url: URL) => IOUtils.toInputStream("ok", "UTF-8")
    system.spawn(Proto[ResourceStringReactor](res, resolver)
      .withScheduler(JvmScheduler.Key.piggyback))
    assert(res.future.value.get.get == "ok", s"got ${res.future.value}")
  }

  test("resource string should throw an exception") {
    val testError = new Exception
    val res = Promise[String]()
    val resolver: URL => InputStream = url => throw testError
    system.spawn(Proto[ResourceStringReactor](res, resolver)
      .withScheduler(JvmScheduler.Key.piggyback))
    assert(res.future.value.get == Failure(testError), s"got ${res.future.value}")
  }

  override def afterAll() {
    system.shutdown()
  }

}


class ResourceStringReactor(val res: Promise[String], val resolver: URL => InputStream)
extends Reactor[Unit] {
  val net = new Platform.Services.Net(system, resolver)
  val response = net.resourceAsString("http://dummy.url/resource.txt")
  response.ignoreExceptions onEvent { s =>
    res success s
    main.seal()
  }
  response onExcept { case t =>
    res failure t
    main.seal()
  }
}
