/**
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package lagom

import sbt._
import sbtunidoc.Plugin.UnidocKeys._
import sbtunidoc.Plugin.{Genjavadoc, JavaUnidoc, ScalaUnidoc, scalaJavaUnidocSettings}
import sbt.Keys._
import sbt.File
import sbt.ScopeFilter.ProjectFilter

object Scaladoc extends AutoPlugin {

  object CliOptions {
    val scaladocAutoAPI = CliOption("lagom.scaladoc.autoapi", true)
  }

  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin

  override lazy val projectSettings = {
    inTask(doc)(Seq(
      scalacOptions in Compile ++= scaladocOptions(version.value, (baseDirectory in ThisBuild).value),
      autoAPIMappings := CliOptions.scaladocAutoAPI.get
    ))
  }

  def scaladocOptions(ver: String, base: File): List[String] = {
    val urlString = GitHub.url(ver) + "/€{FILE_PATH}.scala"
    val opts = List("-implicits", "-doc-source-url", urlString, "-sourcepath", base.getAbsolutePath)
    opts
  }

}

/**
 * Unidoc settings for root project. Adds unidoc command.
 */
object UnidocRoot extends AutoPlugin {

  override def trigger = noTrigger

  private def projectsAndDependencies(projects: Seq[ProjectReference]): ProjectFilter = {
    //projects.map(p => inDependencies(p, transitive = true, includeRoot = true)).reduce(_ || _)
    projects.map(p => inProjects(p)).reduce(_ || _)
  }

  def settings(javadslProjects: Seq[ProjectReference], scaladslProjects: Seq[ProjectReference]) = {
    inTask(unidoc)(Seq(
      unidocProjectFilter in ScalaUnidoc := projectsAndDependencies(scaladslProjects),
      unidocProjectFilter in JavaUnidoc := projectsAndDependencies(javadslProjects),
      apiMappings in ScalaUnidoc := (apiMappings in (Compile, doc)).value
    ))
  }

  def excludeJavadoc = Set("internal", "protobuf", "scaladsl")

  private val allGenjavadocSources = Def.taskDyn {
    (sources in (Genjavadoc, doc)).all((unidocScopeFilter in (JavaUnidoc, unidoc)).value)
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
      |</script>""".stripMargin.replaceAll("\n", "\\\\n").replaceAll("\"", "\\\\\"")

  override lazy val projectSettings = scalaJavaUnidocSettings ++ Seq(
    unidocAllSources in (JavaUnidoc, unidoc) ++= allGenjavadocSources.value,
    unidocAllSources in (JavaUnidoc, unidoc) := {
      (unidocAllSources in (JavaUnidoc, unidoc)).value
        .map(_.filterNot(f => excludeJavadoc.exists(f.getCanonicalPath.contains)))
      },
    scalacOptions in (ScalaUnidoc, unidoc) ++= Seq("-skip-packages", "com.lightbend.lagom.internal"),
    javacOptions in doc := Seq(
      "-windowtitle", "Lagom Services API",
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
    ))

  def packageList(names: String*): String = 
    names.mkString(":")
}



/**
 * Unidoc settings for every multi-project. Adds genjavadoc specific settings.
 */
object Unidoc extends AutoPlugin {

  lazy val GenjavadocCompilerPlugin = config("genjavadocplugin") hide

  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin
  override def projectConfigurations: Seq[Configuration] = Seq(Genjavadoc)

  // Define a new compile task in the genjavadoc configuration that enables genjavadoc
  // This is so that we don't generate the javadoc code on every Scala compile, but only when we actually want to
  // build the javadocs.
  // This means scalac actually will be invoked 3 times any time a publishLocal is done - this can probably be optimised
  // down to two assuming https://github.com/typesafehub/genjavadoc/issues/66 is possible.
  override lazy val projectSettings = inConfig(Genjavadoc)(Defaults.configSettings) ++ Seq(
    ivyConfigurations += GenjavadocCompilerPlugin,
    libraryDependencies += "com.typesafe.genjavadoc" %% "genjavadoc-plugin" % "0.9" % "genjavadocplugin->default(compile)" cross CrossVersion.full,
    scalacOptions in Genjavadoc ++= Seq(
      "-P:genjavadoc:out=" + (target.value / "java"),
      "-P:genjavadoc:fabricateParams=false"
    ),
    scalacOptions in Genjavadoc ++=
        update.value.matching(configurationFilter(GenjavadocCompilerPlugin.name)).filter(_.getName.contains("genjavadoc"))
          .map("-Xplugin:" + _.getAbsolutePath),
    sources in Genjavadoc := (sources in Compile).value,
    sources in (Genjavadoc, doc) := {
      val _ = (compile in Genjavadoc).value
      (target.value / "java" ** "*.java").get
    },
    dependencyClasspath in Genjavadoc := (dependencyClasspath in Compile).value
  )

}
