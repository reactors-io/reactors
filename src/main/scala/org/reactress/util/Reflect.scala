package org.reactress
package util



import java.lang.reflect._



object Reflect {

  def instantiate[T](clazz: Class[T], params: Seq[Any]): T = {
    val ctor = matchingConstructor(clazz, params)
    ctor.setAccessible(true)
    ctor.newInstance(params.asInstanceOf[Seq[AnyRef]]: _*)
  }

  def matchingConstructor[T](clazz: Class[T], params: Seq[Any]): Constructor[T] = try {
    if (params.isEmpty) clazz.getDeclaredConstructor()
    else {
      def matches(c: Constructor[_]): Boolean = {
        val cargs = c.getParameterTypes
        cargs.length == params.length && {
          val cit = cargs.iterator
          val pit = params.iterator
          while (cit.hasNext) {
            val cls = cit.next()
            val obj = pit.next()
            if (
              !cls.isInstance(obj) &&
              !boxedVersion(cls).isInstance(obj) &&
              !(obj == null && !cls.isPrimitive)
            ) return false
          }
          true
        }
      }
      val cs = clazz.getDeclaredConstructors.filter(matches)
      if (cs.length == 0) error.illegalArg(s"No match for $clazz and $params")
      else if (cs.length > 1) error.illegalArg(s"Multiple matches for $clazz and $params")
      else cs.head.asInstanceOf[Constructor[T]]
    }
  } catch {
    case e: Exception => throw new IllegalArgumentException(s"Could not find constructor for $clazz.", e)
  }

  private val boxedMapping = Map[Class[_], Class[_]](
    classOf[Boolean] -> classOf[java.lang.Boolean],
    classOf[Byte] -> classOf[java.lang.Byte],
    classOf[Char] -> classOf[java.lang.Character],
    classOf[Short] -> classOf[java.lang.Short],
    classOf[Int] -> classOf[java.lang.Integer],
    classOf[Long] -> classOf[java.lang.Long],
    classOf[Float] -> classOf[java.lang.Float],
    classOf[Double] -> classOf[java.lang.Double]
  )

  def boxedVersion(cls: Class[_]) = if (!cls.isPrimitive) cls else boxedMapping(cls)

}
