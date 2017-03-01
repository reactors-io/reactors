package io.reactors
package remote



import java.io._
import java.net._
import org.scalatest.FunSuite
import scala.sys.process._



class TcpRemoteTest extends FunSuite {
  // test("tcp connection established") {
  //   val socket = new Socket("localhost", "9500")
  //   val bufferSize = 10000
  //   val buffer = new Array[Byte](bufferSize)

  //   Seq("java", "-cp", "", "io.reactors.remote.TcpRemoteTest").run()

  //   while (i < 10000) {
  //     val os = socket.getOutputStream
  //     while (i < bufferSize) {
  //       buffer(i) = 1
  //       i += 1
  //     }
  //     os.write(buffer, 0, bufferSize)
  //   }

  //   assert(true)
  // }
}


object TcpRemoteTest {
  // def main(args: Array[String]) {
  //   val serverSocket = new ServerSocket(9500)
  //   val clientSocket = serverSocket.accept()
  //   val is = clientSocket.getInputStream
  //   val bufferSize = 10000
  //   val buffer = new Array[Byte](bufferSize)
  //   var count = 0
  //   do {
  //     count = is.read(buffer, 0, bufferSize)
  //   } while (count > 0)
  // }
}
