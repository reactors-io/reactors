package io.reactors



import scala.collection._
import scala.util.parsing.combinator._



object Platform {

  private[reactors] object parser extends JavaTokenParsers {
    def configuration: Parser[Map[String, Any]] = rep(tuple) ^^ {
      case s: Seq[(String, Any)] => s.toMap
    }
    def tuple: Parser[(String, Any)] = keyName ~ "=" ~ value ^^ {
      case key ~ _ ~ dict => (key, dict)
    }
    def keyName = regex("[a-zA-Z][a-zA-Z0-9_\\-]*".r)
    def value: Parser[Any] = string | int | double | dictionary
    def string: Parser[String] = stringLiteral
    def int: Parser[Int] = wholeNumber ^^ { _.toInt }
    def double: Parser[Double] = floatingPointNumber ^^ { _.toDouble }
    def dictionary: Parser[Map[String, Any]] = "{" ~ configuration ~ "}" ^^ {
      case _ ~ config ~ _ => config
    }

    def simpleParse(s: String): Map[String, Any] = {
      parser.parseAll(parser.configuration, s) match {
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
      paths.toSeq.collect {
        case (p, obj) if p.startsWith(prefix) && p.indexOf('.') != -1 =>
          val parts = p.substring(prefix.length).split(".")
          val group = parts(0)
          val nkey = parts(1)
          (group, (nkey, obj))
      }.groupBy { case (p, _) => p }.toMap.map {
        case (group, elems) =>
          (group, elems.map { case (_, (nkey, obj)) => (nkey, obj) }.toMap)
      }.values.toSeq.map(m => new SimpleConfiguration(m))
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
    def parse(s: String) = new SimpleConfiguration(parser.simpleParse(s))
    def empty = new SimpleConfiguration(Map())
  }
}
