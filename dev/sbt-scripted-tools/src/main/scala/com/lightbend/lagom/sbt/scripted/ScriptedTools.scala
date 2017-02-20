package com.lightbend.lagom.sbt.scripted

import java.io.{ InputStreamReader, BufferedReader }
import java.net.HttpURLConnection

import com.lightbend.lagom.sbt.{ LagomPlugin, NonBlockingInteractionMode, Internal }
import com.lightbend.lagom.core.LagomVersion
import sbt.Keys._
import sbt._
import sbt.complete.Parser

import scala.util.control.NonFatal

object ScriptedTools extends AutoPlugin {
  private val ConnectTimeout = 10000
  private val ReadTimeout = 10000

  override def trigger = allRequirements
  override def requires = LagomPlugin

  object autoImport {
    val validateRequest = inputKey[Response]("Validate the given request")
    val validateFile = inputKey[File]("Validate a file")

    val lagomSbtScriptedLibrary = "com.lightbend.lagom" %% "lagom-sbt-scripted-library" % LagomVersion.current
  }

  import autoImport._

  override def buildSettings: Seq[Setting[_]] = Seq(
    validateRequest := {
      val log = streams.value.log
      val validateRequest = validateRequestParser.parsed

      def attempt(): Response = {
        log.info("Making request on " + validateRequest.uri.get)
        val conn = validateRequest.uri.get.toURL.openConnection().asInstanceOf[HttpURLConnection]
        try {
          conn.setConnectTimeout(ConnectTimeout)
          conn.setReadTimeout(ReadTimeout)
          val status = conn.getResponseCode

          if (validateRequest.shouldBeDown) {
            sys.error(s"Expected request to fail, but returned status $status")
          }

          validateRequest.statusAssertion(status)

          // HttpURLConnection throws FileNotFoundException on getInputStream when the status is 404
          val body = if (status == 404) "" else {
            val br = new BufferedReader(new InputStreamReader(conn.getInputStream))
            Stream.continually(br.readLine()).takeWhile(_ != null).mkString("\n")
          }

          validateRequest.bodyAssertion(body)

          Response(status, body)
        } catch {
          case NonFatal(t) if validateRequest.shouldBeDown =>
            Response(0, "")
        } finally {
          conn.disconnect()
        }
      }

      if (validateRequest.retry) {
        repeatUntilSuccessful(log, attempt())
      } else {
        attempt()
      }
    },
    aggregate in validateRequest := false,
    validateFile := {
      val validateFile = validateFileParser.parsed
      val file = baseDirectory.value / validateFile.file.get
      val log = streams.value.log

      def attempt() = {
        log.info("Validating file " + file)
        val contents = IO.read(file)
        validateFile.assertions(contents)
      }

      if (validateFile.retry) {
        repeatUntilSuccessful(log, attempt())
      } else {
        attempt()
      }
      file
    },
    aggregate in validateFile := false,
    Internal.Keys.interactionMode := NonBlockingInteractionMode
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    scalaVersion := Option(System.getProperty("scala.version")).getOrElse("2.11.7")
  )

  private def repeatUntilSuccessful[T](log: Logger, operation: => T, times: Int = 10): T = {
    try {
      operation
    } catch {
      case NonFatal(t) =>
        if (times <= 1) {
          throw t
        } else {
          log.warn(s"Operation failed, $times attempts left")
          Thread.sleep(500)
          repeatUntilSuccessful(log, operation, times - 1)
        }
    }
  }

  case class Response(status: Int, body: String)

  private case class ValidateRequest(uri: Option[URI] = None, retry: Boolean = false,
    shouldBeDown: Boolean = false,
    statusAssertion: Int => Unit = _ => (),
    bodyAssertion: String => Unit = _ => ())

  private val validateRequestParser: Parser[ValidateRequest] = {
    import complete.DefaultParsers._

    type ApplyOption = ValidateRequest => ValidateRequest

    def optionArg[A](opt: String, parser: Parser[A])(applyOption: A => ApplyOption): Parser[ApplyOption] = {
      (literal(opt) ~> Space ~> parser).map(applyOption)
    }

    def option(opt: String)(applyOption: ApplyOption): Parser[ApplyOption] = {
      literal(opt).map(_ => applyOption)
    }

    def bodyAssertionOption(opt: String, description: String)(assertion: (String, String) => Boolean): Parser[ApplyOption] = {
      optionArg(opt, StringBasic)(expected => v => {
        val oldAssertion = v.bodyAssertion
        v.copy(bodyAssertion = body => {
          // First run the existing assertion
          oldAssertion(body)
          // Now run this assertion
          if (!assertion(body, expected)) sys.error(s"Expected body to $description '$expected' but got '$body'")
        })
      })
    }

    def statusAssertionOption(opt: String, description: String)(assertion: (Int, Int) => Boolean): Parser[ApplyOption] = {
      optionArg(opt, NatBasic)(expected => v => {
        val oldAssertion = v.statusAssertion
        v.copy(statusAssertion = status => {
          oldAssertion(status)
          if (!assertion(status, expected)) sys.error(s"Expected status to $description $expected but got $status")
        })
      })
    }

    val retry = option("retry-until-success")(_.copy(retry = true))
    val shouldBeDown = option("should-be-down")(_.copy(shouldBeDown = true))

    val status = statusAssertionOption("status", "equal")(_ == _)
    val notStatus = statusAssertionOption("not-status", "not equal")(_ != _)

    val contains = bodyAssertionOption("body-contains", "contain")(_.contains(_))
    val notContains = bodyAssertionOption("body-not-contains", "not contain")(!_.contains(_))
    val equals = bodyAssertionOption("body-equals", "equal")(_ == _)
    val notEquals = bodyAssertionOption("body-not-equals", "not equal")(_ != _)
    val matches = bodyAssertionOption("body-matches", "match")((body, regexp) => regexp.r.pattern.matcher(body).matches())
    val notMatches = bodyAssertionOption("body-not-matches", "not match")((body, regexp) => !regexp.r.pattern.matcher(body).matches())

    val uri = basicUri.map(uri => (validateRequest: ValidateRequest) => validateRequest.copy(uri = Some(uri)))

    Space ~> repsep(retry | shouldBeDown | status | notStatus | contains | notContains | matches | notMatches | equals | notEquals | uri, Space).map { options =>
      options.foldLeft(ValidateRequest())((validateRequest, applyOption) => applyOption(validateRequest))
    }.filter(_.uri.isDefined, _ => "No URI supplied")

  }

  private case class ValidateFile(file: Option[String] = None, retry: Boolean = false, assertions: String => Unit = _ => ())

  private val validateFileParser: Parser[ValidateFile] = {
    import complete.DefaultParsers._

    def assertionOption[A](opt: String, parser: Parser[A])(assertion: (String, A) => Unit): Parser[ValidateFile => ValidateFile] = {
      (literal(opt) ~> Space ~> parser).map(expected => (v: ValidateFile) => {
        val oldAssertions = v.assertions
        v.copy(assertions = contents => {
          // First run the existing assertion
          oldAssertions(contents)
          // Now run this assertion
          assertion(contents, expected)
        })
      })
    }

    val retry = literal("retry-until-success").map(_ => (v: ValidateFile) => v.copy(retry = true))

    val lineCount = assertionOption("line-count", NatBasic) { (contents, expected) =>
      val count = contents.linesIterator.size
      if (count != expected) sys.error(s"Expected line count of $expected but got $count")
    }
    val contains = assertionOption("contains", StringBasic)((contents, expected) =>
      if (!contents.contains(expected)) sys.error(s"Expected file to contain '$expected' but got '$contents'"))
    val notContains = assertionOption("not-contains", StringBasic)((contents, expected) =>
      if (contents.contains(expected)) sys.error(s"Expected file to not contain '$expected' but got '$contents'"))

    val file = StringBasic.map(fileName => (v: ValidateFile) => v.copy(file = Some(fileName)))

    Space ~> repsep(retry | lineCount | contains | notContains | file, Space).map { options =>
      options.foldLeft(ValidateFile())((v, applyOption) => applyOption(v))
    }.filter(_.file.isDefined, _ => "No file supplied")

  }

}
