/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api

import java.util.regex.Pattern

import akka.util.ByteString
import play.utils.UriEncoding

import scala.util.parsing.combinator.JavaTokenParsers

case class Path(pathSpec: String, parts: Seq[PathPart], queryParams: Seq[String]) {
  val regex = parts.map(_.expression).mkString.r
  private val dynamicParts = parts.collect {
    case dyn: DynamicPathPart => dyn
  }

  def extract(path: String, query: Map[String, Seq[String]]): Option[Seq[Seq[String]]] = {
    regex.unapplySeq(path).map { partValues =>
      val pathParams = dynamicParts.zip(partValues).map {
        case (part, value) =>
          Seq(if (part.encoded) {
            UriEncoding.decodePathSegment(value, ByteString.UTF_8)
          } else value)
      }
      val qps = queryParams.map { paramName =>
        query.getOrElse(paramName, Nil)
      }
      pathParams ++ qps
    }
  }

  def format(allParams: Seq[Seq[String]]): (String, Map[String, Seq[String]]) = {

    if (dynamicParts.size + queryParams.size != allParams.size) {
      throw new IllegalArgumentException(s"Param number mismatch, attempt to encode ${allParams.size} params into path spec $pathSpec")
    }

    val (resultPathParts, leftOverParams) = parts.foldLeft((Seq.empty[String], allParams)) {
      case ((pathParts, params), StaticPathPart(path)) => (pathParts :+ path, params)
      case ((pathParts, params), DynamicPathPart(name, _, encoded)) =>
        val encodedValue = params.head match {
          case Seq(value) =>
            if (encoded) {
              UriEncoding.encodePathSegment(value, ByteString.UTF_8)
            } else {
              value
            }
          case other => throw new IllegalArgumentException("Illegal attempt to encode zero or multiple parts into a path segment: " + other)
        }
        (pathParts :+ encodedValue, params.tail)
    }
    val path = resultPathParts.mkString

    val queryValues = queryParams.zip(leftOverParams).toMap
    path -> queryValues
  }

}

sealed trait PathPart {
  def expression: String
}

case class DynamicPathPart(name: String, regex: String, encoded: Boolean) extends PathPart {
  def expression = "(" + regex + ")"
}

case class StaticPathPart(path: String) extends PathPart {
  def expression = Pattern.quote(path)
}
object Path {
  private object PathSpecParser extends JavaTokenParsers {
    def namedError[A](p: Parser[A], msg: String): Parser[A] = Parser[A] { i =>
      p(i) match {
        case Failure(_, in) => Failure(msg, in)
        case o              => o
      }
    }

    val identifier = namedError(ident, "Identifier expected")

    def singleComponentPathPart: Parser[DynamicPathPart] = (":" ~> identifier) ^^ {
      case name => DynamicPathPart(name, """[^/]+""", encoded = true)
    }

    def multipleComponentsPathPart: Parser[DynamicPathPart] = ("*" ~> identifier) ^^ {
      case name => DynamicPathPart(name, """.+""", encoded = false)
    }

    def regexSpecification: Parser[String] = "<" ~> """[^>\s]+""".r <~ ">"

    def regexComponentPathPart: Parser[DynamicPathPart] = "$" ~> identifier ~ regexSpecification ^^ {
      case name ~ regex => DynamicPathPart(name, regex, encoded = false)
    }

    def staticPathPart: Parser[StaticPathPart] = """[^:\*\$\?\s]+""".r ^^ {
      case path => StaticPathPart(path)
    }

    def queryParam: Parser[String] = namedError("""[^&]+""".r, "Query parameter name expected")

    def queryParams: Parser[Seq[String]] = "?" ~> repsep(queryParam, "&")

    def pathSpec: Parser[Seq[PathPart]] = "/" ~> (staticPathPart | singleComponentPathPart | multipleComponentsPathPart | regexComponentPathPart).* ^^ {
      case parts => parts match {
        case StaticPathPart(path) :: tail => StaticPathPart(s"/$path") :: tail
        case _                            => StaticPathPart("/") :: parts
      }
    }

    def parser(spec: String): Parser[Path] = pathSpec ~ queryParams.? ^^ {
      case parts ~ queryParams => Path(spec, parts, queryParams.getOrElse(Nil))
    }

  }

  def parse(spec: String): Path = {
    PathSpecParser.parseAll(PathSpecParser.parser(spec), spec) match {
      case PathSpecParser.Success(path, _)  => path
      case PathSpecParser.NoSuccess(msg, _) => throw new IllegalArgumentException(s"Error parsing $spec: $msg")
    }
  }
}
