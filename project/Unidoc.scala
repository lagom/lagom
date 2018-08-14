/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package lagom

import sbt._
import sbt.Keys._

import sbtunidoc.BaseUnidocPlugin.autoImport.{ unidoc, unidocProjectFilter, unidocAllSources }
import sbtunidoc.JavaUnidocPlugin.autoImport.JavaUnidoc
import sbtunidoc.ScalaUnidocPlugin.autoImport.ScalaUnidoc
import sbtunidoc.GenJavadocPlugin.autoImport._

object Unidoc {

  private def packageList(names: String*): String = names.mkString(":")

  private def scaladocOptions(ver: String, base: File): List[String] = {
    val urlString = GitHub.url(ver) + "/€{FILE_PATH}.scala"
    val opts = List("-implicits", "-doc-source-url", urlString, "-sourcepath", base.getAbsolutePath)
    opts
  }

  /**
    * This ensures that we can link to the frames version of a page (ie, instead of api/foo/Bar.html,
    * link to api/index.html?foo/Bar.html), while still being able to also link to a specific method.
    *
    * It checks whether the current window is a class frame (rather than the package frame, or
    * the top level window if not using frames), and if the top window has a hash, takes the
    * current frame to that hash.
    *
    * I'm not sure exactly how this string is processed by what and where, but it seems escaping
    * newlines and double quotes makes it work with javadoc.
    */
  private val framesHashScrollingCode =
    """<script type="text/javascript">
      |  if (window.name == "classFrame" && window.top.location.hash) {
      |    window.location.href = window.top.location.hash;
      |  }
      |</script>"""
        .stripMargin
        .replaceAll("\n", "\\\\n")
        .replaceAll("\"", "\\\\\"")

  /**
    * JDK 1.8.0_121 introduced a restriction that prevents the inclusion of JS inside generated
    * javadoc HTML files. That check can be disabled but requires an extra argument.
    */
  private val JavaBuildVersion = """1\.8\.0_(\d+)""".r

  private val enableScriptsArgs = sys.props.get("java.version") match {
    case Some(JavaBuildVersion(build)) if build.toInt < 121 => Nil
    case _ => Seq("--allow-script-in-comments")
  }

  def settings(scaladslProjects: Seq[Project], javadslProjects: Seq[Project]): Seq[Setting[_]] = Seq(
    // Override the Scala unidoc target to *not* include the Scala version, since we don't cross-build docs
    target in (ScalaUnidoc, unidoc) := target.value / "unidoc",
    unidocAllSources in (JavaUnidoc, unidoc) := {
      (unidocAllSources in (JavaUnidoc, unidoc)).value
        .map(_.filterNot(f => Set("internal", "protobuf", "scaladsl").exists(f.getCanonicalPath.contains)))
    },
    scalacOptions in (ScalaUnidoc, unidoc) ++= Seq("-skip-packages", "com.lightbend.lagom.internal"),
    javacOptions in doc := Seq(
        "-windowtitle", "Lagom Services API",
        // Adding a user agent when we run `javadoc` is necessary to create link docs
        // with Akka (at least, maybe play too) because doc.akka.io is served by Cloudflare
        // which blocks requests without a User-Agent header.
        "-J-Dhttp.agent=Lagom-Unidoc-Javadoc",
        "-link", "https://docs.oracle.com/javase/8/docs/api/",
        "-link", "https://doc.akka.io/japi/akka/current/",
        "-link", "https://doc.akka.io/japi/akka-http/current/",
        "-link", "https://www.playframework.com/documentation/latest/api/java/",
        "-public",
        "-group", "Services API", packageList(
          "com.lightbend.lagom.javadsl",
          "com.lightbend.lagom.javadsl.api",
          "com.lightbend.lagom.javadsl.client",
          "com.lightbend.lagom.javadsl.server",
          "com.lightbend.lagom.javadsl.api.deser",
          "com.lightbend.lagom.javadsl.api.paging"
        ),
        "-group", "Persistence", packageList(
          "com.lightbend.lagom.javadsl.persistence",
          "com.lightbend.lagom.javadsl.persistence.cassandra",
          "com.lightbend.lagom.javadsl.persistence.cassandra.testkit",
          "com.lightbend.lagom.javadsl.persistence.jdbc",
          "com.lightbend.lagom.javadsl.persistence.jdbc.testkit",
          "com.lightbend.lagom.javadsl.persistence.jpa",
          "com.lightbend.lagom.javadsl.persistence.testkit"
        ),
        "-group", "Cluster", packageList(
          "com.lightbend.lagom.javadsl.pubsub",
          "com.lightbend.lagom.javadsl.cluster"
        ),
        "-group", "Message Broker", packageList(
          "com.lightbend.lagom.javadsl.api.broker",
          "com.lightbend.lagom.javadsl.api.broker.kafka",
          "com.lightbend.lagom.javadsl.broker",
          "com.lightbend.lagom.javadsl.broker.kafka"
        ),
        "-noqualifier", "java.lang",
        "-encoding", "UTF-8",
        "-source", "1.8",
        "-notimestamp",
        "-footer", framesHashScrollingCode
      ) ++ enableScriptsArgs
  ) ++ inTask(unidoc)(Seq(
    unidocProjectFilter in ScalaUnidoc := scaladslProjects.map(p => inProjects(p)).reduce(_ || _),
    unidocProjectFilter in JavaUnidoc := javadslProjects.map(p => inProjects(p)).reduce(_ || _),
    autoAPIMappings in ScalaUnidoc := true
  )) ++  inTask(doc)(Seq(
    scalacOptions in Compile ++= scaladocOptions(version.value, (baseDirectory in ThisBuild).value),
    autoAPIMappings := true
  )) ++ inConfig(Genjavadoc)(Defaults.configSettings) ++ Seq(
    ivyConfigurations += (config("genjavadocplugin") hide),
    libraryDependencies += "com.typesafe.genjavadoc" % "genjavadoc-plugin" % "0.11" % "genjavadocplugin->default(compile)" cross CrossVersion.full,
    scalacOptions in Genjavadoc ++= Seq(
      "-P:genjavadoc:out=" + (target.value / "java"),
      "-P:genjavadoc:fabricateParams=false"
    ),
    scalacOptions in Genjavadoc ++=
      update.value.matching(configurationFilter((config("genjavadocplugin") hide).name)).filter(_.getName.contains("genjavadoc"))
        .map("-Xplugin:" + _.getAbsolutePath),
    sources in Genjavadoc := (sources in Compile).value,
    sources in (Genjavadoc, doc) := {
      val _ = (compile in Genjavadoc).value
      (target.value / "java" ** "*.java").get
    },
    dependencyClasspath in Genjavadoc := (dependencyClasspath in Compile).value
  )
}
