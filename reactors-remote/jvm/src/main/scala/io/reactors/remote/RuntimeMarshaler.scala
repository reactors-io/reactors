package io.reactors
package remote



import java.lang.reflect.Modifier
import scala.reflect.ClassTag



object RuntimeMarshaler {
  private val booleanClass = classOf[Boolean]
  private val byteClass = classOf[Byte]
  private val shortClass = classOf[Short]
  private val charClass = classOf[Char]
  private val intClass = classOf[Int]
  private val floatClass = classOf[Float]
  private val longClass = classOf[Long]
  private val doubleClass = classOf[Double]

  def marshalAs[T](clazz: Class[_], obj: T, inputData: Data): Data = {
    internalMarshalAs(clazz, obj, inputData, Reactor.marshalContext)
  }

  private def internalMarshalAs[T](
    clazz: Class[_], obj: T, inputData: Data, context: Reactor.MarshalContext
  ): Data = {
    var data = inputData
    val fields = clazz.getDeclaredFields
    var i = 0
    while (i < fields.length) {
      val field = fields(i)
      if (!Modifier.isTransient(field.getModifiers)) {
        field.setAccessible(true)
        val tpe = field.getType
        if (tpe.isPrimitive) {
          tpe match {
            case RuntimeMarshaler.this.intClass =>
              val v = field.getInt(obj)
              if (data.remainingWriteSize < 4) data = data.flush(-1)
              val pos = data.endPos
              data(pos + 0) = (v & 0x000000ff).toByte
              data(pos + 1) = (v & 0x0000ff00).toByte
              data(pos + 2) = (v & 0x00ff0000).toByte
              data(pos + 3) = (v & 0xff000000).toByte
              data.endPos += 4
            case RuntimeMarshaler.this.longClass =>
              val v = field.getLong(obj)
              if (data.remainingWriteSize < 8) data = data.flush(-1)
              val pos = data.endPos
              data(pos + 0) = (v & 0x00000000000000ffL).toByte
              data(pos + 1) = (v & 0x000000000000ff00L).toByte
              data(pos + 2) = (v & 0x0000000000ff0000L).toByte
              data(pos + 3) = (v & 0x00000000ff000000L).toByte
              data(pos + 4) = (v & 0x000000ff00000000L).toByte
              data(pos + 5) = (v & 0x0000ff0000000000L).toByte
              data(pos + 6) = (v & 0x00ff000000000000L).toByte
              data(pos + 7) = (v & 0xff00000000000000L).toByte
              data.endPos += 8
            case RuntimeMarshaler.this.doubleClass =>
              val v = java.lang.Double.doubleToRawLongBits(field.getDouble(obj))
              if (data.remainingWriteSize < 8) data = data.flush(-1)
              val pos = data.endPos
              data(pos + 0) = (v & 0x00000000000000ffL).toByte
              data(pos + 1) = (v & 0x000000000000ff00L).toByte
              data(pos + 2) = (v & 0x0000000000ff0000L).toByte
              data(pos + 3) = (v & 0x00000000ff000000L).toByte
              data(pos + 4) = (v & 0x000000ff00000000L).toByte
              data(pos + 5) = (v & 0x0000ff0000000000L).toByte
              data(pos + 6) = (v & 0x00ff000000000000L).toByte
              data(pos + 7) = (v & 0xff00000000000000L).toByte
              data.endPos += 8
            case RuntimeMarshaler.this.floatClass =>
              val v = java.lang.Float.floatToRawIntBits(field.getFloat(obj))
              if (data.remainingWriteSize < 4) data = data.flush(-1)
              val pos = data.endPos
              data(pos + 0) = (v & 0x000000ff).toByte
              data(pos + 1) = (v & 0x0000ff00).toByte
              data(pos + 2) = (v & 0x00ff0000).toByte
              data(pos + 3) = (v & 0xff000000).toByte
              data.endPos += 4
            case RuntimeMarshaler.this.byteClass =>
              val v = field.getByte(obj)
              if (data.remainingWriteSize < 1) data = data.flush(-1)
              val pos = data.endPos
              data(pos + 0) = v
              data.endPos += 1
            case RuntimeMarshaler.this.booleanClass =>
              val v = field.getBoolean(obj)
              if (data.remainingWriteSize < 1) data = data.flush(-1)
              val pos = data.endPos
              data(pos) = if (v) 1 else 0
              data.endPos += 1
            case RuntimeMarshaler.this.charClass =>
              val v = field.getChar(obj)
              if (data.remainingWriteSize < 2) data = data.flush(-1)
              val pos = data.endPos
              data(pos + 0) = (v & 0x000000ff).toByte
              data(pos + 1) = (v & 0x0000ff00).toByte
              data.endPos += 2
            case RuntimeMarshaler.this.shortClass =>
              val v = field.getInt(obj)
              if (data.remainingWriteSize < 2) data = data.flush(-1)
              val pos = data.endPos
              data(pos + 0) = (v & 0x000000ff).toByte
              data(pos + 1) = (v & 0x0000ff00).toByte
              data.endPos += 2
          }
        } else if (tpe.isArray) {
          sys.error("Array marshaling is currently not supported.")
        } else {
          val value = field.get(obj)
          val marshalType = Modifier.isFinal(field.getClass.getModifiers)
          data = internalMarshal(value, data, marshalType, context)
        }
      }
      i += 1
    }
    val superclazz = clazz.getSuperclass
    if (superclazz != null) marshalAs(superclazz, obj, data)
    else data
  }

  def marshal[T](obj: T, inputData: Data, marshalType: Boolean = true): Data = {
    internalMarshal(obj, inputData, marshalType, Reactor.marshalContext)
  }

  private def internalMarshal[T](
    obj: T, inputData: Data, marshalType: Boolean, context: Reactor.MarshalContext
  ): Data = {
    var data = inputData
    val clazz = obj.getClass
    if (marshalType) {
      val name = obj.getClass.getName
      val typeLength = name.length + 1
      if (typeLength > data.remainingWriteSize) data = data.flush(typeLength)
      val pos = data.endPos
      var i = 0
      while (i < name.length) {
        data(pos + i) = name.charAt(i).toByte
        i += 1
      }
      data(pos + i) = 0
      data.endPos += typeLength
    } else {
      if (data.remainingWriteSize < 1) data = data.flush(-1)
      data(data.endPos) = 0
      data.endPos += 1
    }
    internalMarshalAs(clazz, obj, data, context)
  }

  def unmarshal[T: ClassTag](inputData: Data, unmarshalType: Boolean = true): T = {
    // TODO: Figure out what to do about the input data change.
    internalUnmarshal(inputData, unmarshalType, Reactor.marshalContext)
  }

  def internalUnmarshal[T: ClassTag](
    inputData: Data, unmarshalType: Boolean, context: Reactor.MarshalContext
  ): T = {
    // TODO: Figure out what to do about the input data change.
    var data = inputData
    var klazz = implicitly[ClassTag[T]].runtimeClass
    if (unmarshalType) {
      val stringBuffer = context.stringBuffer
      if (data.remainingReadSize < 1) data = data.fetch()
      var last = data(data.startPos)
      if (last == 0) sys.error("Data does not contain a type signature.")
      var i = data.startPos
      var until = data.startPos + data.remainingReadSize
      stringBuffer.setLength(0)
      while (last != 0) {
        last = data(i)
        if (last != 0) stringBuffer.append(last.toChar)
        i += 1
        if (i == until) {
          data = data.fetch()
          if (last != 0) {
            i = data.startPos
            until = data.startPos + data.remainingReadSize
          }
        }
      }
      val klazzName = stringBuffer.toString
      klazz = Class.forName(klazzName)
    }
    println(klazz)
    val instance = unsafe.allocateInstance(klazz)
    // TODO: Unmarshal fields.
    instance.asInstanceOf[T]
  }
}
