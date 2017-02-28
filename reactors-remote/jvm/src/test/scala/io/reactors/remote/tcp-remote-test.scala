package io.reactors
package remote



import java.io._
import java.net._
import org.scalatest.FunSuite



class TcpRemoteTest extends FunSuite {
  ignore("tcp connection established") {
    val socket = new Socket("localhost", "9500")
    val bufferSize = 10000
    val buffer = new Array[Byte](bufferSize)

    while (i < 10000) {
      val os = socket.getOutputStream
      while (i < bufferSize) {
        buffer(i) = 1
        i += 1
      }
      os.write(buffer, 0, bufferSize)
    }

    assert(true)
  }
}


object TcpRemoteTest {
  def main(args: Array[String]) {
    val serverSocket = new ServerSocket(9500)
    val clientSocket = serverSocket.accept()
    clientSocket.getInputStream
  }
}
