package io.reactors
package remote






object RuntimeMarshaler {
  def marshalSuper[T](clazz: Class[_], obj: T, inputBuffer: AnyRef): AnyRef = {
    marshalSuperInternal(clazz, obj, inputBuffer)
  }

  private def marshalSuperInternal[T](
    clazz: Class[_], obj: T, inputBuffer: AnyRef
  ): AnyRef = {
    var buffer = inputBuffer
    val fields = clazz.getFields
    var i = 0
    while (i < fields.length) {
      val field = fields(i)
      field.setAccessible(true)
      if (field.getType.isPrimitive) {
        // TODO
        ???
      } else {
        val value = field.get(obj)
        buffer = marshalInternal(value, buffer)
      }
      i += 1
    }
    val superclazz = clazz.getSuperclass
    if (superclazz != null) marshalSuper(superclazz, obj, buffer)
    else buffer
  }

  def marshal[T](obj: T, inputBuffer: AnyRef): AnyRef = {
    marshalInternal(obj, inputBuffer)
  }

  private def marshalInternal[T](obj: T, inputBuffer: AnyRef): AnyRef = {
    var buffer = inputBuffer
    // TODO: Write class identifier.
    ???
    val clazz = obj.getClass
    marshalSuperInternal(clazz, obj, buffer)
  }
}
