package io.reactors
package remote



import org.scalatest.FunSuite



class RuntimeMarshalerTest extends FunSuite {
  test("marshal empty non-final class") {
    val data = new Data.Linked(128, 128)
    RuntimeMarshaler.marshal(new NonFinalEmpty, data)
    println(data.raw.mkString(", "))
  }

  test("marshal empty final class") {
    val data = new Data.Linked(128, 128)
    RuntimeMarshaler.marshal(new FinalEmpty, data)
    println(data.raw.mkString(", "))
  }
}


class NonFinalEmpty


final class FinalEmpty
