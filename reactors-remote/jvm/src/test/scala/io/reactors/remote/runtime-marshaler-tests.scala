package io.reactors
package remote



import io.reactors.common.Cell
import org.scalatest.FunSuite



class RuntimeMarshalerTest extends FunSuite {
  test("marshal empty non-final class") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new NonFinalEmpty, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[NonFinalEmpty](cell)
    assert(obj.isInstanceOf[NonFinalEmpty])
  }

  test("marshal empty final class") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new FinalEmpty, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[FinalEmpty](cell)
    assert(obj.isInstanceOf[FinalEmpty])
  }

  test("marshal single integer field non-final class") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new NonFinalSingleInt(15), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[NonFinalSingleInt](cell)
    assert(obj.x == 15)
  }

  test("marshal single integer field final class") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new FinalSingleInt(15), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[FinalSingleInt](cell)
    assert(obj.x == 15)
  }

  test("marshal single long field class") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new SingleLong(15), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleLong](cell)
    assert(obj.x == 15)
  }

  test("marshal single int field class, when buffer is small") {
    val data = new Data.Linked(16, 16)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new FinalSingleInt(15), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[FinalSingleInt](cell)
    assert(obj.x == 15)
  }

  test("marshal single long field class, when buffer is small") {
    val data = new Data.Linked(16, 16)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new SingleLong(15), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleLong](cell)
    assert(obj.x == 15)
  }

  test("marshal single double field class") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new SingleDouble(15.0), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleDouble](cell)
    assert(obj.x == 15.0)
  }

  test("marshal single double field class, when buffer is small") {
    val data = new Data.Linked(16, 16)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new SingleDouble(15.0), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleDouble](cell)
    assert(obj.x == 15.0)
  }

  test("marshal single float field class") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new SingleFloat(15.0f), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleFloat](cell)
    assert(obj.x == 15.0f)
  }

  test("marshal single float field class, when buffer is small") {
    val data = new Data.Linked(16, 16)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new SingleFloat(15.0f), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleFloat](cell)
    assert(obj.x == 15.0f)
  }

  test("marshal single byte field class") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new SingleByte(7), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleByte](cell)
    assert(obj.x == 7)
  }

  test("marshal single boolean field class") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new SingleBoolean(true), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleBoolean](cell)
    assert(obj.x == true)
  }

  test("marshal single char field class") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new SingleChar('a'), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleChar](cell)
    assert(obj.x == 'a')
  }

  test("marshal single short field class") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new SingleShort(17), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[SingleShort](cell)
    assert(obj.x == 17)
  }

  test("marshal mixed primitive field class") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new MixedPrimitives(17, 9, 2.1, true, 8.11f, 'd'), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[MixedPrimitives](cell)
    assert(obj.x == 17)
    assert(obj.y == 9)
    assert(obj.z == 2.1)
    assert(obj.b == true)
    assert(obj.f == 8.11f)
    assert(obj.c == 'd')
  }

  test("marshal object with a final class object field") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new FinalClassObject(new FinalSingleInt(17)), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[FinalClassObject](cell)
    assert(obj.inner.x == 17)
  }

  test("marshal recursive object") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(new RecursiveObject(7, new RecursiveObject(5, null)), data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[RecursiveObject](cell)
    assert(obj.x == 7 && obj.tail.x == 5 && obj.tail.tail == null)
  }

  test("marshal null") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    RuntimeMarshaler.marshal(null, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[AnyRef](cell)
    assert(obj == null)
  }

  test("marshal a cyclic object") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val cyclic = new RecursiveObject(7, null)
    cyclic.tail = cyclic
    RuntimeMarshaler.marshal(cyclic, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[RecursiveObject](cell)
    assert(obj.tail eq obj)
    assert(obj.x == 7)
  }

  test("marshal a cyclic pair of objects") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val a = new RecursiveObject(7, null)
    val b = new RecursiveObject(11, null)
    a.tail = b
    b.tail = a
    RuntimeMarshaler.marshal(a, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[RecursiveObject](cell)
    assert(obj.x == 7)
    assert(obj.tail.x == 11)
    assert(obj.tail.tail eq obj)
  }

  test("marshal an inherited class") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val obj = new InheritedClass(17, 11)
    RuntimeMarshaler.marshal(obj, data)
    println(data.byteString)
    val result = RuntimeMarshaler.unmarshal[InheritedClass](cell)
    assert(result.y == 17)
    assert(result.x == 11)
  }

  test("marshal an object pair") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val pair = new CyclicObjectPair(7,
      new CyclicObjectPair(11, null, null),
      new CyclicObjectPair(17, null, null)
    )
    pair.o1.o1 = pair
    pair.o2.o2 = pair
    RuntimeMarshaler.marshal(pair, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[CyclicObjectPair](cell)
    assert(obj.x == 7)
    assert(obj.o1.x == 11)
    assert(obj.o2.x == 17)
    assert(obj.o1.o1 == obj)
    assert(obj.o2.o2 == obj)
  }

  test("marshal an object with an array") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new ArrayObject(10)
    for (i <- 0 until 10) input.array(i) = i + 11
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[ArrayObject](cell)
    assert(obj.array != null)
    for (i <- 0 until 10) assert(input.array(i) == i + 11)
  }

  test("marshal an object with a big array") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new ArrayObject(256)
    for (i <- 0 until 256) input.array(i) = i + 17
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[ArrayObject](cell)
    assert(obj.array != null)
    for (i <- 0 until 256) assert(input.array(i) == i + 17)
  }

  test("marshal an object with a null array") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new VarArrayObject(null)
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[VarArrayObject](cell)
    assert(obj.array == null)
  }

  test("marshal an int array") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new Array[Int](10)
    for (i <- 0 until 10) input(i) = i + 3
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[Array[Int]](cell)
    assert(obj.length == 10)
    for (i <- 0 until 10) assert(obj(i) == i + 3)
  }

  test("marshal a big int array") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new Array[Int](256)
    for (i <- 0 until 256) input(i) = i + 3
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[Array[Int]](cell)
    assert(obj.length == 256)
    for (i <- 0 until 256) assert(obj(i) == i + 3)
  }

  test("marshal a long array") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new Array[Long](256)
    for (i <- 0 until 256) input(i) = i + 3
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[Array[Long]](cell)
    assert(obj.length == 256)
    for (i <- 0 until 256) assert(obj(i) == i + 3)
  }

  test("marshal an object with a long array") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new LongArrayObject(256)
    for (i <- 0 until 256) input.array(i) = i + 3
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[LongArrayObject](cell)
    assert(obj.array.length == 256)
    for (i <- 0 until 256) assert(obj.array(i) == i + 3)
  }

  test("marshal an object with a double array") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new DoubleArrayObject(256)
    for (i <- 0 until 256) input.array(i) = i + 3.5
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[DoubleArrayObject](cell)
    assert(obj.array.length == 256)
    for (i <- 0 until 256) assert(obj.array(i) == i + 3.5)
  }

  test("marshal an object with a float array") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new FloatArrayObject(256)
    for (i <- 0 until 256) input.array(i) = i + 3.5f
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[FloatArrayObject](cell)
    assert(obj.array.length == 256)
    for (i <- 0 until 256) assert(obj.array(i) == i + 3.5f)
  }

  test("marshal an object with a byte array") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new ByteArrayObject(256)
    for (i <- 0 until 256) input.array(i) = (i + 3).toByte
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[ByteArrayObject](cell)
    assert(obj.array.length == 256)
    for (i <- 0 until 256) assert(obj.array(i) == (i + 3).toByte)
  }

  test("marshal an object with a boolean array") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new BooleanArrayObject(256)
    for (i <- 0 until 256) input.array(i) = i % 3 != 0
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[BooleanArrayObject](cell)
    assert(obj.array.length == 256)
    for (i <- 0 until 256) assert(obj.array(i) == (i % 3 != 0))
  }

  test("marshal an object with a char array") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new CharArrayObject(256)
    for (i <- 0 until 256) input.array(i) = i.toChar
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[CharArrayObject](cell)
    assert(obj.array.length == 256)
    for (i <- 0 until 256) assert(obj.array(i) == i.toChar)
  }

  test("marshal an object with a short array") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new ShortArrayObject(256)
    for (i <- 0 until 256) input.array(i) = i.toShort
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[ShortArrayObject](cell)
    assert(obj.array.length == 256)
    for (i <- 0 until 256) assert(obj.array(i) == i.toShort)
  }

  test("marshal an object with a object array") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new ObjectArrayObject(256)
    for (i <- 0 until 256) input.array(i) = new SingleLong(i)
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[ObjectArrayObject](cell)
    assert(obj.array.length == 256)
    for (i <- 0 until 256) assert(obj.array(i).x == i, s"$i == ${obj.array(i)}")
  }

  test("marshal an object with a final object array") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new FinalObjectArrayObject(256)
    for (i <- 0 until 256) input.array(i) = new FinalSingleInt(i)
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[FinalObjectArrayObject](cell)
    assert(obj.array.length == 256)
    for (i <- 0 until 256) assert(obj.array(i).x == i, s"$i == ${obj.array(i)}")
  }

  test("marshal an array of repeated and null objects") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new Array[AnyRef](256)
    for (i <- 0 until 256) input(i) = i match {
      case i if i % 5 == 0 => null
      case i if i % 6 == 0 => input(i - 5)
      case i if i % 11 == 0 => new SingleLong(i)
      case _ => new FinalSingleInt(i)
    }
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val array = RuntimeMarshaler.unmarshal[Array[AnyRef]](cell)
    assert(array.length == 256)
    for (i <- 0 until 256) i match {
      case i if i % 5 == 0 =>
        assert(array(i) == null)
      case i if i % 6 == 0 =>
        assert(array(i) eq array(i - 5))
        input(i) match {
          case null =>
            assert(array(i) == null)
          case obj: FinalSingleInt =>
            assert(array(i).asInstanceOf[FinalSingleInt].x == obj.x)
          case obj: SingleLong =>
            assert(array(i).asInstanceOf[SingleLong].x == obj.x)
        }
      case i if i % 11 == 0 =>
        assert(array(i).isInstanceOf[SingleLong])
        assert(array(i).asInstanceOf[SingleLong].x == i)
      case _ =>
        assert(array(i).asInstanceOf[FinalSingleInt].x == i)
    }
  }

  test("marshal an array pointing to itself") {
    val data = new Data.Linked(128, 128)
    val cell = new Cell[Data](data)
    val input = new Array[AnyRef](256)
    for (i <- 0 until 256) input(i) = input
    RuntimeMarshaler.marshal(input, data)
    println(data.byteString)
    val obj = RuntimeMarshaler.unmarshal[Array[AnyRef]](cell)
    assert(obj.array.length == 256)
    for (i <- 0 until 256) assert(obj(i) eq obj)
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
  val x: Int, var y: Short, val z: Double, val b: Boolean, val f: Float, val c: Char
)


class FinalClassObject(val inner: FinalSingleInt)


class RecursiveObject(val x: Int, var tail: RecursiveObject)


class BaseClass(val x: Int)


class InheritedClass(val y: Int, px: Int) extends BaseClass(px)


class CyclicObjectPair(val x: Int, var o1: CyclicObjectPair, var o2: CyclicObjectPair)


class ArrayObject(length: Int) {
  val array = new Array[Int](length)
}

class VarArrayObject(var array: Array[Int])

class LongArrayObject(length: Int) {
  val array = new Array[Long](length)
}

class DoubleArrayObject(length: Int) {
  val array = new Array[Double](length)
}

class FloatArrayObject(length: Int) {
  val array = new Array[Float](length)
}

class ByteArrayObject(length: Int) {
  val array = new Array[Byte](length)
}

class BooleanArrayObject(length: Int) {
  val array = new Array[Boolean](length)
}

class CharArrayObject(length: Int) {
  val array = new Array[Char](length)
}

class ShortArrayObject(length: Int) {
  val array = new Array[Short](length)
}

class ObjectArrayObject(length: Int) {
  val array = new Array[SingleLong](length)
}

class FinalObjectArrayObject(length: Int) {
  val array = new Array[FinalSingleInt](length)
}
