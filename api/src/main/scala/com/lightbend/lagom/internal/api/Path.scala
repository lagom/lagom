/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api

import java.util.regex.Pattern

import akka.util.ByteString
import com.lightbend.lagom.javadsl.api.Descriptor.{ CallId, NamedCallId, PathCallId, RestCallId }
import com.lightbend.lagom.javadsl.api.deser.RawId.PathParam
import com.lightbend.lagom.javadsl.api.deser.{ RawId, RawIdDescriptor }
import org.pcollections.{ PSequence, PMap, HashTreePMap, TreePVector }
import play.utils.UriEncoding

import scala.collection.JavaConverters._
import scala.util.parsing.combinator.JavaTokenParsers

case class Path(parts: Seq[PathPart], queryParams: Seq[String]) {
  val regex = parts.map(_.expression).mkString.r
  private val dynamicParts = parts.collect {
    case dyn: DynamicPathPart => dyn
  }

  def extract(path: String, query: Map[String, Seq[String]]): Option[RawId] = {
    regex.unapplySeq(path).map { partValues =>
      val pathParams = TreePVector.from(dynamicParts.zip(partValues).map {
        case (part, value) =>
          val decoded = if (part.encoded) {
            UriEncoding.decodePathSegment(value, ByteString.UTF_8)
          } else value
          PathParam.of(part.name, decoded)
      }.asJava)
      val qps: PMap[String, PSequence[String]] = HashTreePMap.from(queryParams.map { paramName =>
        paramName -> TreePVector.from(query.getOrElse(paramName, Nil).asJava)
      }.toMap.asJava)
      RawId.of(pathParams, qps)
    }
  }

  def format(rawId: RawId): (String, Map[String, Seq[String]]) = {
    val (resultPathParts, _) = parts.foldLeft((Seq.empty[String], rawId.pathParams().asScala.toSeq)) {
      case ((pathParts, params), StaticPathPart(path)) => (pathParts :+ path, params)
      case ((pathParts, params), DynamicPathPart(name, _, encoded)) =>
        val encodedValue = params.headOption match {
          case Some(value) =>
            if (encoded) {
              UriEncoding.encodePathSegment(value.value, ByteString.UTF_8)
            } else {
              value.value
            }
          case None => throw new IllegalArgumentException("RawId does not contain required path param name: " + name)
        }
        (pathParts :+ encodedValue, params.tail)
    }
    val path = resultPathParts.mkString
    val queryParams = rawId.queryParams().asScala.collect {
      case (name, values) if !values.isEmpty =>
        name -> values.asScala.toSeq
    }.toMap
    path -> queryParams
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

    def parser: Parser[Path] = pathSpec ~ queryParams.? ^^ {
      case parts ~ queryParams => Path(parts, queryParams.getOrElse(Nil))
    }

  }

  def fromCallId(callId: CallId): Path = {
    callId match {
      case rest: RestCallId =>
        Path.parse(rest.pathPattern)
      case path: PathCallId =>
        Path.parse(path.pathPattern)
      case named: NamedCallId =>
        val name = named.name
        val path = if (name.startsWith("/")) name else "/" + name
        Path(Seq(StaticPathPart(path)), Nil)
    }
  }

  def parse(spec: String): Path = {
    PathSpecParser.parseAll(PathSpecParser.parser, spec) match {
      case PathSpecParser.Success(path, _)  => path
      case PathSpecParser.NoSuccess(msg, _) => throw new IllegalArgumentException(s"Error parsing $spec: $msg")
    }
  }
}
