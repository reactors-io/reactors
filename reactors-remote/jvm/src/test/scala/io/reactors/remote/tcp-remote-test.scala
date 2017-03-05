package io.reactors
package remote



import java.io._
import java.net._
import org.scalatest.FunSuite
import scala.sys.process._



class TcpRemoteTest extends FunSuite {
  test("tcp connection established") {
    val proc = Seq(
      "java", "-cp", sys.props("java.class.path"), "io.reactors.remote.TcpRemoteTest"
    ).run()
    Thread.sleep(3000)

    val socket = new Socket("localhost", 9500)
    val bufferSize = TcpRemoteTest.bufferSize
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


object TcpRemoteTest {
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

    val ch: Channel[Int] = null
    ch ! 1

    case class Foo(x: Int);
    (null: Channel[Foo]) ! Foo(1)

    trait Base {
      var x: Int = 0
    }
    case class Bar(y: Int) extends Base;
    (null: Channel[Bar]) ! Bar(2)
  }
}
