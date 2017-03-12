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

  test("marshal single long field class, when buffer is small") {
    val data = new Data.Linked(16, 16)
    RuntimeMarshaler.marshal(new SingleLong(15), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleLong](data)
    assert(obj.x == 15)
  }

  test("marshal single double field class") {
    val data = new Data.Linked(128, 128)
    RuntimeMarshaler.marshal(new SingleDouble(15.0), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleDouble](data)
    assert(obj.x == 15.0)
  }

  test("marshal single double field class, when buffer is small") {
    val data = new Data.Linked(16, 16)
    RuntimeMarshaler.marshal(new SingleDouble(15.0), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleDouble](data)
    assert(obj.x == 15.0)
  }

  test("marshal single float field class") {
    val data = new Data.Linked(128, 128)
    RuntimeMarshaler.marshal(new SingleFloat(15.0f), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleFloat](data)
    assert(obj.x == 15.0f)
  }

  test("marshal single float field class, when buffer is small") {
    val data = new Data.Linked(16, 16)
    RuntimeMarshaler.marshal(new SingleFloat(15.0f), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleFloat](data)
    assert(obj.x == 15.0f)
  }

  test("marshal single byte field class") {
    val data = new Data.Linked(128, 128)
    RuntimeMarshaler.marshal(new SingleByte(7), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleByte](data)
    assert(obj.x == 7)
  }

  test("marshal single boolean field class") {
    val data = new Data.Linked(128, 128)
    RuntimeMarshaler.marshal(new SingleBoolean(true), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleBoolean](data)
    assert(obj.x == true)
  }

  test("marshal single char field class") {
    val data = new Data.Linked(128, 128)
    RuntimeMarshaler.marshal(new SingleChar('a'), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleChar](data)
    assert(obj.x == 'a')
  }

  test("marshal single short field class") {
    val data = new Data.Linked(128, 128)
    RuntimeMarshaler.marshal(new SingleShort(17), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleShort](data)
    assert(obj.x == 17)
  }

  test("marshal mixed primitive field class") {
    val data = new Data.Linked(128, 128)
    RuntimeMarshaler.marshal(new MixedPrimitives(17, 9, 2.1, true, 8.11f), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[MixedPrimitives](data)
    assert(obj.x == 17)
    assert(obj.y == 9)
    assert(obj.z == 2.1)
    assert(obj.b == true)
    assert(obj.f == 8.11f)
  }
}


class NonFinalEmpty


final class FinalEmpty


class NonFinalSingleInt(val x: Int)


final class FinalSingleInt(val x: Int)


class SingleLong(val x: Long)


class SingleDouble(val x: Double)


class SingleFloat(val x: Float)


class SingleByte(val x: Byte)


class SingleBoolean(val x: Boolean)


class SingleChar(val x: Char)


class SingleShort(val x: Short)


class MixedPrimitives(
  val x: Int, var y: Short, val z: Double, val b: Boolean, val f: Float
)
