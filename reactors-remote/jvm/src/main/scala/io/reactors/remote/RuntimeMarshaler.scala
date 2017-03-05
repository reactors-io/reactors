package io.reactors
package remote






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
    marshalAsInternal(clazz, obj, inputData)
  }

  private def marshalAsInternal[T](
    clazz: Class[_], obj: T, inputData: Data
  ): Data = {
    var data = inputData
    val fields = clazz.getDeclaredFields
    var i = 0
    while (i < fields.length) {
      val field = fields(i)
      field.setAccessible(true)
      val tpe = field.getType
      if (tpe.isPrimitive) {
        tpe match {
          case intClass =>
            ???
          case longClass =>
            ???
          case doubleClass =>
            ???
          case floatClass =>
            ???
          case byteClass =>
            ???
          case booleanClass =>
            ???
          case charClass =>
            ???
          case shortClass =>
            ???
        }
      } else {
        val value = field.get(obj)
        data = marshalInternal(value, data)
      }
      i += 1
    }
    val superclazz = clazz.getSuperclass
    if (superclazz != null) marshalAs(superclazz, obj, data)
    else data
  }

  def marshal[T](obj: T, inputData: Data): Data = {
    marshalInternal(obj, inputData)
  }

  private def marshalInternal[T](obj: T, inputData: Data): Data = {
    var data = inputData
    // TODO: Write class identifier.
    ???
    val clazz = obj.getClass
    marshalAsInternal(clazz, obj, data)
  }
}
