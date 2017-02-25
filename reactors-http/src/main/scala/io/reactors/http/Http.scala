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

  private def getOrCreateServer(
    port: Int, workers: Option[Channel[Http.Connection]]
  ): Http.Instance = servers.synchronized {
    if (!servers.contains(port)) {
      val reactorUid = Reactor.self.uid
      val requestChannel = if (workers != None) {
        workers.get
      } else {
        val requests = system.channels.daemon.open[Http.Connection]
        requests.events.onEvent(connection => connection.accept())
        requests.channel
      }
      val instance = new Http.Instance(port, reactorUid, requestChannel)
      Reactor.self.sysEvents onMatch {
        case ReactorTerminated => instance.stop()
      }
      servers(port) = instance
    }
    servers(port)
  }

  def at(port: Int): Http.Adapter = getOrCreateAdapter(port, None)

  def parallel(port: Int, workers: Channel[Http.Connection]): Http.Adapter = {
    getOrCreateAdapter(port, Some(workers))
  }

  private[reactors] def getOrCreateAdapter(
    port: Int, workers: Option[Channel[Http.Connection]]
  ): Http.Adapter = {
    val adapter = getOrCreateServer(port, workers)
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

  trait Connection {
    def accept(): Unit
  }

  object Connection {
    private[reactors] class Wrapper (val handler: NanoHTTPD#ClientHandler)
    extends Connection {
      def accept() = handler.run()
    }
  }

  trait Request {
    def headers: Map[String, String]
    def parameters: Map[String, Seq[String]]
    def method: Method
    def uri: String
    def inputStream: InputStream
  }

  object Request {
    private[reactors] class Wrapper(val session: IHTTPSession) extends Request {
      def headers = session.getHeaders.asScala
      def parameters = session.getParameters.asScala.map {
        case (name, values) => (name, values.asScala)
      }
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
    def html(route: String)(handler: Request => String): Unit
    def resource(route: String)(mime: String)(handler: Request => InputStream): Unit
  }

  private[reactors] class Instance (
    val port: Int,
    val reactorUid: Long,
    val requests: Channel[Connection]
  ) extends NanoHTTPD(port) with Adapter {
    private val handlers = mutable.Map[String, IHTTPSession => Response]()
    private val runner = new NanoHTTPD.AsyncRunner {
      def closeAll() {}
      def closed(handler: NanoHTTPD#ClientHandler) {}
      def exec(handler: NanoHTTPD#ClientHandler) {
        requests ! new Connection.Wrapper(handler)
      }
    }

    handlers("#") = defaultErrorHandler
    setAsyncRunner(runner)
    start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)

    private def defaultErrorHandler(session: IHTTPSession): Response = {
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
      val handler = handlers.synchronized {
        handlers.get(route) match {
          case Some(handler) => handler
          case None => handlers("#")
        }
      }
      handler(session)
    }

    def text(route: String)(handler: Request => String): Unit = handlers.synchronized {
      val sessionHandler: IHTTPSession => Response = session => {
        val text = handler(new Request.Wrapper(session))
        NanoHTTPD.newFixedLengthResponse(
          NanoHTTPD.Response.Status.OK, "text/plain", text)
      }
      handlers(route) = sessionHandler
    }

    def html(route: String)(handler: Request => String): Unit = handlers.synchronized {
      val sessionHandler: IHTTPSession => Response = session => {
        val text = handler(new Request.Wrapper(session))
        NanoHTTPD.newFixedLengthResponse(
          NanoHTTPD.Response.Status.OK, "text/html", text)
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
