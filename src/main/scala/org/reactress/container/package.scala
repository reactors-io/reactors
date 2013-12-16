package org.reactress



import scala.reflect.ClassTag



package object container {

  abstract class Arrayable[@spec(Int, Long) T] {
    val classTag: ClassTag[T]
    val nil: T
    def newArray(sz: Int): Array[T]
  }

  object Arrayable {
  
    implicit def arrayableRef[T >: Null <: AnyRef: ClassTag]: Arrayable[T] = new Arrayable[T] {
      val classTag = implicitly[ClassTag[T]]
      val nil = null
      def newArray(sz: Int) = new Array[T](sz)
    }
  
    implicit val arrayableLong: Arrayable[Long] = new Arrayable[Long] {
      val classTag = implicitly[ClassTag[Long]]
      val nil = Long.MinValue
      def newArray(sz: Int) = Array.fill[Long](sz)(nil)
    }
  
    implicit val arrayableDouble: Arrayable[Double] = new Arrayable[Double] {
      val classTag = implicitly[ClassTag[Double]]
      val nil = Double.NaN
      def newArray(sz: Int) = Array.fill[Double](sz)(nil)
    }
  
    implicit val arrayableInt: Arrayable[Int] = new Arrayable[Int] {
      val classTag = implicitly[ClassTag[Int]]
      val nil = Int.MinValue
      def newArray(sz: Int) = Array.fill[Int](sz)(nil)
    }
  
  }

}