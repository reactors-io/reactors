package io.reactors
package protocol



import org.scalatest._
import scala.collection._
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._



class BackpressureProtocolsSpec extends FunSuite {
  val system = ReactorSystem.default("backpressure-protocols")

  test("open a backpressure channel and send event") {
  }
}
