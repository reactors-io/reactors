package io.reactors
package remote



import java.net._
import org.scalatest.FunSuite
import scala.sys.process._



class TcpImplementationTests extends FunSuite {
  test("local tcp connection established") {
    val proc = Seq(
      "java", "-cp", sys.props("java.class.path"),
      "io.reactors.remote.TcpImplementationTest"
    ).run()
    Thread.sleep(3000)
    val socket = new Socket("localhost", 9500)
    val bufferSize = TcpImplementationTest.bufferSize
    val totalBatches = 1000000
    val buffer = new Array[Byte](bufferSize)
    try {
      var j = 0
      while (j < totalBatches) {
        val os = socket.getOutputStream
        var i = 0
        while (i < bufferSize) {
          buffer(i) = 1
          i += 1
        }
        os.write(buffer, 0, bufferSize)
        j += 1
      }
    } finally {
      println("producer: sent all the batches")
      Thread.sleep(3000)
      proc.destroy()
    }
    assert(true)
  }
}


object TcpImplementationTest {
  val bufferSize = 50000

  def main(args: Array[String]) {
    val serverSocket = new ServerSocket(9500)
    val clientSocket = serverSocket.accept()
    val is = clientSocket.getInputStream
    val buffer = new Array[Byte](bufferSize)
    val check_period = 50000
    var round = 0L
    var count = 0
    val startTime = System.nanoTime()
    do {
      count = is.read(buffer, 0, bufferSize)
      round += 1
      if (round % check_period == 0) {
        val totalTime = (System.nanoTime() - startTime) / 1000000.0
        println(s"time: ${round * bufferSize / totalTime} bytes/ms")
      }
    } while (count > 0)
  }
}


class TcpRemoteTest extends FunSuite {
  val system = ReactorSystem.default("test-system")

  test("data chunk pool reallocates the same chunk") {
    val tcp = new TcpTransport(system)
    val buffer = new TcpTransport.SendBuffer(tcp, null, null)
    val data8 = tcp.dataPool.allocate(buffer, 3)
    assert(data8.totalSize == 8)
    data8.startPos = 2
    tcp.dataPool.deallocate(data8)
    val data8again = tcp.dataPool.allocate(buffer, 5)
    assert(data8 eq data8again)
    assert(data8again.startPos == 0)
  }

  test("data chunk pool returns correct sizes") {
    val tcp = new TcpTransport(system)
    val buffer = new TcpTransport.SendBuffer(tcp, null, null)
    val data8 = tcp.dataPool.allocate(buffer, 1)
    assert(data8.totalSize == 8)
    val data16 = tcp.dataPool.allocate(buffer, 10)
    assert(data16.totalSize == 16)
    val data32 = tcp.dataPool.allocate(buffer, 24)
    assert(data32.totalSize == 32)
    val data64 = tcp.dataPool.allocate(buffer, 41)
    assert(data64.totalSize == 64)
    val data128 = tcp.dataPool.allocate(buffer, 101)
    assert(data128.totalSize == 128)
    val data1024 = tcp.dataPool.allocate(buffer, 513)
    assert(data1024.totalSize == 1024)
    val data2048 = tcp.dataPool.allocate(buffer, 2048)
    assert(data2048.totalSize == 2048)
  }
}
