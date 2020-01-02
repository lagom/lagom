/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.sbt.scripted

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection

import com.lightbend.lagom.sbt.Internal
import com.lightbend.lagom.sbt.LagomPlugin
import com.lightbend.lagom.sbt.NonBlockingInteractionMode
import com.lightbend.lagom.core.LagomVersion
import sbt.Keys._
import sbt._
import sbt.complete.Parser

import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

object ScriptedTools extends AutoPlugin {
  private val ConnectTimeout = 10000
  private val ReadTimeout    = 10000

  override def trigger = allRequirements

  override def requires = LagomPlugin

  object autoImport {
    val validateRequest = inputKey[Response]("Validate the given request")
    val validateFile    = inputKey[File]("Validate a file")

    val lagomSbtScriptedLibrary = "com.lightbend.lagom" %% "lagom-sbt-scripted-library" % LagomVersion.current
  }

  import autoImport._

  override def buildSettings: Seq[Setting[_]] = Seq(
    validateRequest := validateRequestImpl.evaluated,
    aggregate in validateRequest := false,
    validateFile := validateFileImpl.evaluated,
    aggregate in validateFile := false,
    Internal.Keys.interactionMode := NonBlockingInteractionMode,
    // This is copy & pasted from project/AkkaSnapshotRepositories so that scripted tests
    // can use Akka snapshot repositories as well. If you change it here, remember to keep
    // project/AkkaSnapshotRepositories in sync.
    resolvers ++= (sys.props.get("lagom.build.akka.version") match {
      case Some(_) =>
        Seq(
          "akka-snapshot-repository".at("https://repo.akka.io/snapshots"),
          "akka-http-snapshot-repository".at("https://dl.bintray.com/akka/snapshots/")
        )
      case None => Seq.empty
    }),
    scalaVersion := sys.props.get("scala.version").getOrElse("2.12.10")
  )

  private def validateRequestImpl = Def.inputTask {
    val log             = streams.value.log
    val validateRequest = validateRequestParser.parsed
    val uri             = validateRequest.uri.get
    val seenStackTraces = scala.collection.mutable.Set.empty[Int]

    def attempt(): Response = {
      log.info(s"Making request on $uri")
      val conn = uri.toURL.openConnection().asInstanceOf[HttpURLConnection]
      try {
        conn.setConnectTimeout(ConnectTimeout)
        conn.setReadTimeout(ReadTimeout)
        val status = conn.getResponseCode

        if (validateRequest.shouldBeDown) {
          throw ShouldBeDownException(status)
        }

        validateRequest.statusAssertion(status)

        // HttpURLConnection throws FileNotFoundException on getInputStream when the status is 404
        // HttpURLConnection throws IOException on getInputStream when the status is 500
        val body =
          Try {
            val br = new BufferedReader(new InputStreamReader(conn.getInputStream))
            Stream.continually(br.readLine()).takeWhile(_ != null).mkString("\n")
          }.recover {
            case _ => ""
          }.get

        validateRequest.bodyAssertion(body)

        Response(status, body)
      } catch {
        case NonFatal(ShouldBeDownException(status)) =>
          val msg = s"Expected server to be down but request completed with status $status."
          log.error(msg)
          sys.error(msg)
        case NonFatal(_) if validateRequest.shouldBeDown =>
          Response(0, "")
        case NonFatal(t) =>
          val msg = s"Failed to make a request on $uri; cause: ${t.getMessage} (${t.getClass})"
          if (seenStackTraces.add(t.##)) {
            log.error(t.getStackTrace.map(_.toString).mkString(s"$msg; stack:\n  ", "\n  ", ""))
          } else {
            log.error(msg)
          }
          sys.error(msg)
      } finally {
        conn.disconnect()
      }
    }

    if (validateRequest.retry) {
      repeatUntilSuccessful(log, attempt())
    } else {
      attempt()
    }
  }

  private def validateFileImpl = Def.inputTask {
    val validateFile = validateFileParser.parsed
    val file         = baseDirectory.value / validateFile.file.get
    val log          = streams.value.log

    def attempt() = {
      log.info(s"Validating file $file")
      val contents = IO.read(file)
      validateFile.assertions(contents)
    }

    if (validateFile.retry) {
      repeatUntilSuccessful(log, attempt())
    } else {
      attempt()
    }
    file
  }

  private def repeatUntilSuccessful[T](log: Logger, operation: => T, times: Int = 30): T = {
    try operation
    catch {
      case NonFatal(t) =>
        if (times <= 1) {
          throw t
        } else {
          log.warn(s"Operation failed, ${times - 1} attempts left")
          SECONDS.sleep(1)
          repeatUntilSuccessful(log, operation, times - 1)
        }
    }
  }

  case class ShouldBeDownException(actualStatus: Int) extends RuntimeException

  case class Response(status: Int, body: String)

  private case class ValidateRequest(
      uri: Option[URI] = None,
      retry: Boolean = false,
      shouldBeDown: Boolean = false,
      statusAssertion: Int => Unit = _ => (),
      bodyAssertion: String => Unit = _ => ()
  )

  private val validateRequestParser: Parser[ValidateRequest] = {
    import complete.DefaultParsers._

    type ApplyOption = ValidateRequest => ValidateRequest

    def optionArg[A](opt: String, parser: Parser[A])(applyOption: A => ApplyOption): Parser[ApplyOption] = {
      (literal(opt) ~> Space ~> parser).map(applyOption)
    }

    def option(opt: String)(applyOption: ApplyOption): Parser[ApplyOption] = {
      literal(opt).map(_ => applyOption)
    }

    def bodyAssertionOption(opt: String, description: String)(
        assertion: (String, String) => Boolean
    ): Parser[ApplyOption] = {
      optionArg(opt, StringBasic)(expected =>
        v => {
          val oldAssertion = v.bodyAssertion
          v.copy(bodyAssertion = body => {
            // First run the existing assertion
            oldAssertion(body)
            // Now run this assertion
            if (!assertion(body, expected)) sys.error(s"Expected body to $description '$expected' but got '$body'")
          })
        }
      )
    }

    def statusAssertionOption(opt: String, description: String)(
        assertion: (Int, Int) => Boolean
    ): Parser[ApplyOption] = {
      optionArg(opt, NatBasic)(expected =>
        v => {
          val oldAssertion = v.statusAssertion
          v.copy(statusAssertion = status => {
            oldAssertion(status)
            if (!assertion(status, expected)) sys.error(s"Expected status to $description $expected but got $status")
          })
        }
      )
    }

    val retry        = option("retry-until-success")(_.copy(retry = true))
    val shouldBeDown = option("should-be-down")(_.copy(shouldBeDown = true))

    val status    = statusAssertionOption("status", "equal")(_ == _)
    val notStatus = statusAssertionOption("not-status", "not equal")(_ != _)

    val contains    = bodyAssertionOption("body-contains", "contain")(_.contains(_))
    val notContains = bodyAssertionOption("body-not-contains", "not contain")(!_.contains(_))
    val equals      = bodyAssertionOption("body-equals", "equal")(_ == _)
    val notEquals   = bodyAssertionOption("body-not-equals", "not equal")(_ != _)
    val matches =
      bodyAssertionOption("body-matches", "match")((body, regexp) => regexp.r.pattern.matcher(body).matches())
    val notMatches =
      bodyAssertionOption("body-not-matches", "not match")((body, regexp) => !regexp.r.pattern.matcher(body).matches())

    val uri = basicUri.map(uri => (validateRequest: ValidateRequest) => validateRequest.copy(uri = Some(uri)))

    Space ~> repsep(
      retry | shouldBeDown | status | notStatus | contains | notContains | matches | notMatches | equals | notEquals | uri,
      Space
    ).map { options =>
        options.foldLeft(ValidateRequest())((validateRequest, applyOption) => applyOption(validateRequest))
      }
      .filter(_.uri.isDefined, _ => "No URI supplied")
  }

  private case class ValidateFile(
      file: Option[String] = None,
      retry: Boolean = false,
      assertions: String => Unit = _ => ()
  )

  private val validateFileParser: Parser[ValidateFile] = {
    import complete.DefaultParsers._

    def assertionOption[A](opt: String, parser: Parser[A])(
        assertion: (String, A) => Unit
    ): Parser[ValidateFile => ValidateFile] = {
      (literal(opt) ~> Space ~> parser).map(expected =>
        (v: ValidateFile) => {
          val oldAssertions = v.assertions
          v.copy(assertions = contents => {
            // First run the existing assertion
            oldAssertions(contents)
            // Now run this assertion
            assertion(contents, expected)
          })
        }
      )
    }

    val retry = literal("retry-until-success").map(_ => (v: ValidateFile) => v.copy(retry = true))

    val lineCount = assertionOption("line-count", NatBasic) { (contents, expected) =>
      val count = contents.linesIterator.size
      if (count != expected) sys.error(s"Expected line count of $expected but got $count")
    }
    val contains = assertionOption("contains", StringBasic)((contents, expected) =>
      if (!contents.contains(expected)) sys.error(s"Expected file to contain '$expected' but got '$contents'")
    )
    val notContains = assertionOption("not-contains", StringBasic)((contents, expected) =>
      if (contents.contains(expected)) sys.error(s"Expected file to not contain '$expected' but got '$contents'")
    )

    val file = StringBasic.map(fileName => (v: ValidateFile) => v.copy(file = Some(fileName)))

    Space ~> repsep(retry | lineCount | contains | notContains | file, Space)
      .map { options =>
        options.foldLeft(ValidateFile())((v, applyOption) => applyOption(v))
      }
      .filter(_.file.isDefined, _ => "No file supplied")
  }
}
