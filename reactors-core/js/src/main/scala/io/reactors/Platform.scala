package io.reactors



import scala.annotation.unchecked
import scala.collection._
import scala.util.parsing.combinator._



object Platform {

  private[reactors] object HoconParser extends JavaTokenParsers {
    def configuration: Parser[Map[String, Any]] = rep(tuple) ^^ {
      case s: Seq[Map[String, Any]] @unchecked => s.foldLeft(Map[String, Any]())(_ ++ _)
    }
    def tuple: Parser[Map[String, Any]] = keyName ~ "=" ~ value ^^ {
      case name ~ _ ~ (dict: Map[String, Any] @unchecked) =>
        for ((k, v) <- dict) yield (s"$name.$k", v)
      case name ~ _ ~ v =>
        Map(name -> v)
    }
    def keyName = regex("[a-zA-Z][a-zA-Z0-9_\\-]*".r)
    def value: Parser[Any] = string | double | int | dictionary
    def string: Parser[String] =
      "\"" ~ regex("[ -!#-~]*".r) ~ "\"" ^^ {
        case _ ~ content ~ _ => content
      }
    def int: Parser[Int] = wholeNumber ^^ { _.toInt }
    def double: Parser[Double] = decimalNumber ^^ { _.toDouble }
    def dictionary: Parser[Map[String, Any]] = "{" ~ configuration ~ "}" ^^ {
      case _ ~ config ~ _ => config
    }

    def simpleParse(s: String): Map[String, Any] = {
      parseAll(configuration, s.trim) match {
        case Success(m, _) => m
        case _ => sys.error(s"Cannot parse '$s'.")
      }
    }
  }

  private[reactors] class SimpleConfiguration(val paths: Map[String, Any])
  extends Configuration {
    def int(path: String): Int = paths(path).asInstanceOf[Int]
    def string(path: String): String = paths(path).asInstanceOf[String]
    def double(path: String): Double = paths(path).asInstanceOf[Double]
    def children(path: String): Seq[Configuration] = {
      val prefix = path + "."
      def isDict(p: String): Boolean = {
        p.startsWith(prefix) && p.substring(prefix.length).indexOf('.') != -1
      }
      paths.toSeq.collect {
        case (p, obj) if isDict(p) =>
          val parts = p.substring(prefix.length).split("\\.")
          val group = parts(0)
          val nkey = parts(1)
          (group, (nkey, obj))
      }.groupBy { case (p, _) => p }.toMap.map {
        case (group, elems) =>
          (group, elems.map { case (_, (nkey, obj)) => (nkey, obj) }.toMap)
      }.values.toList.map(m => new SimpleConfiguration(m))
    }
    def withFallback(other: Configuration): Configuration = {
      val newPaths = mutable.Map[String, Any](paths.toSeq: _*)
      for ((p, obj) <- other.asInstanceOf[SimpleConfiguration].paths) {
        if (!newPaths.contains(p)) newPaths(p) = obj
      }
      new SimpleConfiguration(newPaths)
    }
  }

  private[reactors] val configurationFactory = new Configuration.Factory {
    def parse(s: String) = new SimpleConfiguration(HoconParser.simpleParse(s))
    def empty = new SimpleConfiguration(Map())
  }

  private[reactors] val machineConfiguration = s"""
    system = {
      num-processors = ${1}
    }
  """

  private[reactors] val defaultConfiguration = """
    pickler = "io.reactors.pickle.NoPickler"
    remote = {
    }
    debug-api = {
      name = "io.reactors.DebugApi$Zero"
    }
    scheduler = {
      spindown = {
        initial = 0
        min = 0
        max = 0
        cooldown-rate = 8
        mutation-rate = 0.15
        test-threshold = 32
        test-iterations = 3
      }
      default = {
        budget = 50
        unschedule-count = 0
      }
    }
    system = {
      net = {
        parallelism = 1
      }
    }
  """

  private[reactors] def registerDefaultSchedulers(b: ReactorSystem.Bundle): Unit = {
    b.registerScheduler(JsScheduler.Key.default, JsScheduler.default)
  }

  private[reactors] lazy val defaultScheduler = JsScheduler.default

  object Services {
    /** Contains I/O-related services.
     */
    class Io(val system: ReactorSystem) extends Protocol.Service {
      val defaultCharset = Charset.defaultCharset.name

      def shutdown() {}
    }

    /** Contains common network protocol services.
     */
    class Net(val system: ReactorSystem, private val resolver: URL => InputStream)
    extends Protocol.Service {
      def shutdown() {}
    }
  }

  private[reactors] def inetAddress(host: String, port: String) = ???
}
