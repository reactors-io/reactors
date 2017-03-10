package io.reactors



import io.reactors.remote.macros.Synthesizer
import sun.misc.Unsafe



package object remote {
  import scala.language.experimental.macros

  implicit class ChannelOps[T](val ch: Channel[T]) extends AnyVal {
    def !(x: T): Unit = macro Synthesizer.send
  }

  val unsafe = {
    val unsafeInstanceField = classOf[Unsafe].getDeclaredField("theUnsafe")
    unsafeInstanceField.setAccessible(true)
    unsafeInstanceField.get(null).asInstanceOf[Unsafe]
  }
}
