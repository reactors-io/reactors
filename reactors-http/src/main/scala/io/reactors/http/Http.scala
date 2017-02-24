package io.reactors
package http



import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.InputStream
import scala.collection._
import scala.collection.JavaConverters._



class Http(val system: ReactorSystem) extends Protocol.Service {
  private val servers = mutable.Map[Int, Http.Instance]()

  private def getOrCreateServer(port: Int): Http.Instance =
    servers.synchronized {
      if (!servers.contains(port)) {
        val reactorUid = Reactor.self.uid
        val requests = system.channels.daemon.open[NanoHTTPD#ClientHandler]
        val instance = new Http.Instance(port, reactorUid, requests.channel)
        requests.events.onEvent(handler => handler.run())
        Reactor.self.sysEvents onMatch {
          case ReactorTerminated => instance.stop()
        }
        servers(port) = instance
      }
      servers(port)
    }

  def at(port: Int): Http.Adapter = {
    val adapter = getOrCreateServer(port)
    if (Reactor.self.uid != adapter.reactorUid)
      sys.error("Server already at $port, and owned by reactor ${adapter.reactorUid}.")
    adapter
  }

  def shutdown() {
    servers.synchronized {
      for ((port, server) <- servers) {
        server.stop()
      }
    }
  }
}


object Http {
  sealed trait Method
  case object Get extends Method
  case object Put extends Method

  trait Request {
    def headers: Map[String, String]
    def method: Method
    def uri: String
    def inputStream: InputStream
  }

  object Request {
    private[reactors] class Wrapper(val session: IHTTPSession) extends Request {
      def headers = session.getHeaders.asScala
      def method = session.getMethod match {
        case NanoHTTPD.Method.GET => Get
        case NanoHTTPD.Method.PUT => Put
        case _ => sys.error("Method ${session.getMethod} is not supported.")
      }
      def uri = session.getUri
      def inputStream = session.getInputStream
    }
  }

  trait Adapter {
    def text(route: String)(handler: Request => String): Unit
    def resource(route: String)(mime: String)(handler: Request => InputStream): Unit
  }

  private[reactors] class Instance (
    val port: Int,
    val reactorUid: Long,
    val requests: Channel[NanoHTTPD#ClientHandler]
  ) extends NanoHTTPD(port) with Adapter {
    private val handlers = mutable.Map[String, IHTTPSession => Response]()
    private val runner = new NanoHTTPD.AsyncRunner {
      def closeAll() {}
      def closed(handler: NanoHTTPD#ClientHandler) {}
      def exec(handler: NanoHTTPD#ClientHandler) {
        requests ! handler
      }
    }

    setAsyncRunner(runner)
    start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)

    private def errorHandler(session: IHTTPSession): Response = {
      val content = """
      <html>
      <head><title>HTTP 404 Not Found</title>
        <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1">
        <meta name="description" content="Error 404 File not found">
      </head>
      <body>
        <h1>Requested resource not found.</h1>
      </body>
      </html>
      """
      NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, "text/html", content)
    }

    override def serve(session: IHTTPSession): Response = {
      val route = session.getUri
      handlers.get(route) match {
        case Some(handler) => handler(session)
        case None => errorHandler(session)
      }
    }

    def text(route: String)(handler: Request => String): Unit = handlers.synchronized {
      val sessionHandler: IHTTPSession => Response = session => {
        val text = handler(new Request.Wrapper(session))
        NanoHTTPD.newFixedLengthResponse(
          NanoHTTPD.Response.Status.OK, "text/plain", text)
      }
      handlers(route) = sessionHandler
    }

    def resource(route: String)(mime: String)(handler: Request => InputStream): Unit =
      handlers.synchronized {
        val sessionHandler: IHTTPSession => Response = session => {
          val inputStream = handler(new Request.Wrapper(session))
          NanoHTTPD.newChunkedResponse(
            NanoHTTPD.Response.Status.OK, mime, inputStream)
        }
        handlers(route) = sessionHandler
      }
  }
}
