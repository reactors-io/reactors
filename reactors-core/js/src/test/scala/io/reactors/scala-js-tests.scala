package io.reactors
package scalajs



import org.scalatest._



class ScalaJsTest extends FunSuite {
  test("create reactor system and call shutdown") {
    val system = ReactorSystem.default("")
    system.shutdown()
  }
}
