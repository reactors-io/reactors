package io.reactors



import com.typesafe.config._
import java.net.InetSocketAddress
import java.lang.reflect._
import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.blocking
import scala.util.Success
import scala.util.Failure
import scala.util.Try
import sun.misc.Unsafe



object Platform {
  class HoconConfiguration(val config: Config) extends Configuration {
    def int(path: String): Int = config.getInt(path)
    def string(path: String): String = config.getString(path)
    def double(path: String): Double = config.getDouble(path)
    def list(path: String): Seq[Configuration] = {
      val elems = config.getObjectList(path).iterator().asScala.toSeq
      elems.map(c => new HoconConfiguration(c.toConfig))
    }
    def children(path: String): Seq[Configuration] = {
      config.getConfig("remote").root.values.asScala.collect {
        case c: ConfigObject => c.toConfig
      }.map(c => new HoconConfiguration(c)).toSeq
    }
    def withFallback(other: Configuration): Configuration = {
      new HoconConfiguration(this.config.withFallback(
        other.asInstanceOf[HoconConfiguration].config))
    }
  }

  private[reactors] val configurationFactory = new Configuration.Factory {
    def parse(s: String) = new HoconConfiguration(ConfigFactory.parseString(s))
    def empty = new HoconConfiguration(ConfigFactory.empty)
  }

  private[reactors] val machineConfiguration = s"""
    system = {
      num-processors = ${Runtime.getRuntime.availableProcessors}
    }
  """

  private[reactors] val defaultConfiguration = """
    pickler = "io.reactors.pickle.JavaSerializationPickler"
    remote = {
      default-schema = "local"
      transports = [
        {
          schema = "udp"
          transport = "io.reactors.remote.UdpTransport"
          host = "localhost"
          port = 0
        }
      ]
    }
    debug-api = {
      name = "io.reactors.debugger.ZeroDebugApi"
      port = 9500
      repl = {
        expiration = 120
        expiration-check-period = 60
      }
      session = {
        expiration = 240
        expiration-check-period = 150
      }
      delta-debugger = {
        window-size = 1024
      }
    }
    error-handler = {
      name = "io.reactors.DefaultErrorHandler"
    }
    scheduler = {
      lagging = {
        enabled = 1
      }
      default = {
        budget = 64
        postschedule-count = 100
      }
    }
    system = {
      net = {
        parallelism = 8
      }
    }
  """

  private[reactors] def registerDefaultSchedulers(b: ReactorSystem.Bundle): Unit = {
    b.registerScheduler(JvmScheduler.Key.globalExecutionContext,
      JvmScheduler.globalExecutionContext)
    b.registerScheduler(JvmScheduler.Key.default, JvmScheduler.default)
    b.registerScheduler(JvmScheduler.Key.newThread, JvmScheduler.newThread)
    b.registerScheduler(JvmScheduler.Key.piggyback, JvmScheduler.piggyback)
  }

  private[reactors] lazy val defaultScheduler = JvmScheduler.default

  private[reactors] def inetAddress(host: String, port: Int) =
    new InetSocketAddress(host, port)

  private[reactors] object Reflect {
    def instantiate[T](clazz: Class[T], args: scala.Array[Any]): T = {
      // Java-only version.
      instantiate(clazz, args.toSeq)
    }

    def instantiate[T](clazz: Class[T], args: Seq[Any]): T = {
      val ctor = matchingConstructor(clazz, args)
      ctor.setAccessible(true)
      ctor.newInstance(args.asInstanceOf[Seq[AnyRef]]: _*)
    }

    def instantiate[T](name: String, args: Seq[Any]): T = {
      val clazz = Class.forName(name).asInstanceOf[Class[T]]
      instantiate(clazz, args)
    }

    private def matchingConstructor[T](
      cls: Class[T], args: Seq[Any]
    ): Constructor[T] = try {
      if (args.isEmpty) cls.getDeclaredConstructor()
      else {
        def matches(c: Constructor[_]): Boolean = {
          val cargs = c.getParameterTypes
          cargs.length == args.length && {
            val cit = cargs.iterator
            val pit = args.iterator
            while (cit.hasNext) {
              val cls = cit.next()
              val obj = pit.next()
              if (
                !cls.isInstance(obj) &&
                !boxedVersion(cls).isInstance(obj) &&
                !(obj == null && !cls.isPrimitive)
              ) return false
            }
            true
          }
        }
        val cs = cls.getDeclaredConstructors.filter(matches)
        if (cs.length == 0) exception.illegalArg(s"No match for $cls and $args.")
        else if (cs.length > 1)
          exception.illegalArg(s"Multiple matches for $cls and $args.")
        else cs.head.asInstanceOf[Constructor[T]]
      }
    } catch {
      case e: Exception =>
        throw new IllegalArgumentException(s"Could not find constructor for $cls.", e)
    }

    private val boxedMapping = Map[Class[_], Class[_]](
      classOf[Boolean] -> classOf[java.lang.Boolean],
      classOf[Byte] -> classOf[java.lang.Byte],
      classOf[Char] -> classOf[java.lang.Character],
      classOf[Short] -> classOf[java.lang.Short],
      classOf[Int] -> classOf[java.lang.Integer],
      classOf[Long] -> classOf[java.lang.Long],
      classOf[Float] -> classOf[java.lang.Float],
      classOf[Double] -> classOf[java.lang.Double]
    )

    private def boxedVersion(cls: Class[_]) =
      if (!cls.isPrimitive) cls else boxedMapping(cls)
  }

  private[reactors] def javaReflect = Reflect

  private[reactors] trait Reflectable

  private[reactors] def newSnapshotMap[K, V] = new TrieMap[K, V]

  val unsafe = {
    val unsafeInstanceField = classOf[Unsafe].getDeclaredField("theUnsafe")
    unsafeInstanceField.setAccessible(true)
    unsafeInstanceField.get(null).asInstanceOf[Unsafe]
  }
}
