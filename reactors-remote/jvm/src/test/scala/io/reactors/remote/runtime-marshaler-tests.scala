package io.reactors
package remote



import org.scalatest.FunSuite



class RuntimeMarshalerTest extends FunSuite {
  test("marshal empty non-final class") {
    val data = new Data.Linked(128, 128)
    RuntimeMarshaler.marshal(new NonFinalEmpty, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[NonFinalEmpty](data)
    assert(obj.isInstanceOf[NonFinalEmpty])
  }

  test("marshal empty final class") {
    val data = new Data.Linked(128, 128)
    RuntimeMarshaler.marshal(new FinalEmpty, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[FinalEmpty](data)
    assert(obj.isInstanceOf[FinalEmpty])
  }

  test("marshal single integer field non-final class") {
    val data = new Data.Linked(128, 128)
    RuntimeMarshaler.marshal(new NonFinalSingle(15), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[NonFinalSingle](data)
    assert(obj.x == 15)
  }
}


class NonFinalEmpty


final class FinalEmpty


final class NonFinalSingle(val x: Int)
