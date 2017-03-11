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
    RuntimeMarshaler.marshal(new NonFinalSingleInt(15), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[NonFinalSingleInt](data)
    assert(obj.x == 15)
  }

  test("marshal single integer field final class") {
    val data = new Data.Linked(128, 128)
    RuntimeMarshaler.marshal(new FinalSingleInt(15), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[FinalSingleInt](data)
    assert(obj.x == 15)
  }

  test("marshal single long field class") {
    val data = new Data.Linked(128, 128)
    RuntimeMarshaler.marshal(new SingleLong(15), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleLong](data)
    assert(obj.x == 15)
  }

  test("marshal single int field class, when buffer is small") {
    val data = new Data.Linked(16, 16)
    RuntimeMarshaler.marshal(new FinalSingleInt(15), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[FinalSingleInt](data)
    assert(obj.x == 15)
  }
}


class NonFinalEmpty


final class FinalEmpty


class NonFinalSingleInt(val x: Int)


final class FinalSingleInt(val x: Int)


class SingleLong(val x: Long)
