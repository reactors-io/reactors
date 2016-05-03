package io.reactors
package protocol



import io.reactors.test._
import org.scalacheck._
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Gen.choose
import org.scalatest._
import scala.collection._
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._



class BalancerProtocolsSpec extends FunSuite {
  val system = ReactorSystem.default("balancer-protocols")

  test("default balancer") {

  }
}


class BalancerProtocolsCheck
extends Properties("ServerProtocols") with ExtendedProperties {
  val system = ReactorSystem.default("check-system")

  val sizes = detChoose(0, 256)

  property("round-robin balancer") = forAllNoShrink(sizes) { num =>
    stackTraced {
      true
    }
  }
}
