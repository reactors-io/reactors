package io.reactors.common.concurrent



import sun.misc.Unsafe
import scala.annotation.switch
import scala.annotation.tailrec



class ByteswapTree[K <: AnyRef, V <: AnyRef] {
  private def unsafe: Unsafe = Platform.unsafe
}


object ByteswapTree {
}
