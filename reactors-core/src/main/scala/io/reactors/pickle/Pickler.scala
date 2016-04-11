package io.reactors
package pickle



import java.io._
import java.nio.ByteBuffer



/** Pickles an object into a byte buffer, so that it can be sent over the wire.
 */
trait Pickler {
  def pickle[@spec(Int, Long, Double) T](x: T, buffer: ByteBuffer): Unit
  def depickle[@spec(Int, Long, Double) T](buffer: ByteBuffer): T
}


object Pickler {
  private[pickle] class ByteBufferOutputStream(val buf: ByteBuffer)
  extends OutputStream {
    def write(b: Int): Unit = buf.put(b.toByte)
    override def write(bytes: Array[Byte], off: Int, len: Int): Unit = {
      buf.put(bytes, off, len)
    }
  }

  private[pickle] class ByteBufferInputStream(val buffer: ByteBuffer)
  extends InputStream {
    def read() = buffer.get()
    override def read(dst: Array[Byte], offset: Int, length: Int) = {
      val count = math.min(buffer.remaining, length)
      if (count == 0) -1
      else {
        buffer.get(dst, offset, length)
        count
      }
    }
  }
}
