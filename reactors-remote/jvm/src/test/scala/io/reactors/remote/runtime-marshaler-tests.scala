package io.reactors
package remote



import org.scalatest.FunSuite



class RuntimeMarshalerTest extends FunSuite {
  test("marshal empty non-final class") {
    val data = new Data.Linked(128, 128)
    RuntimeMarshaler.marshal(new NonFinalEmpty, data)
    println(data.byteString)
    RuntimeMarshaler.unmarshal[NonFinalEmpty](data)
  }

  test("marshal empty final class") {
    val data = new Data.Linked(128, 128)
    RuntimeMarshaler.marshal(new FinalEmpty, data)
    println(data.byteString)
    RuntimeMarshaler.unmarshal[FinalEmpty](data)
  }
}


class NonFinalEmpty


final class FinalEmpty
