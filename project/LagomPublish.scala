import sbt.Keys.{ name, publishTo, version }
import sbt.{ Def, FeedbackProvidedException, Resolver, Task, taskKey }

object LagomPublish {

  val validatePublishSettings = taskKey[Unit]("Validate Lagom settings to publish released artifacts.")

  val validatePublishSettingsTask: Def.Initialize[Task[Unit]] = Def.task {
    val resolverValue: Option[Resolver] = publishTo.value
    val inReleaseVersion: Boolean = !version.value.contains("SNAPSHOT")

    // the following implements the rules described in https://github.com/lagom/lagom/issues/1496#issuecomment-408398508
    // TODO: improve rules and validations depending on the version (SNAPSHOT vs release)
    (name.value, resolverValue) match {
      case (_, None) => throw new PublishValidationFailed("`publishTo` not set.")
      case ("lagom-sbt-plugin", x) =>
        // see https://github.com/sbt/sbt-bintray/blob/7c93bacaae3ffc128564ceacb6e73ec4486525dd/src/main/scala/Bintray.scala#L16-L29 for
        // details on the syntax of Bintray Resolver names.
        if (inReleaseVersion && x.get.name != "Bintray-Sbt-Publish-lagom-sbt-plugin-releases-lagom-sbt-plugin") {
          throw new PublishValidationFailed("Raw(Bintray-Sbt-Publish-lagom-sbt-plugin-releases-lagom-sbt-plugin)", x.get)
        }
        // TODO: Add a validation for "lagom-sbt-plugin" when the version is a snapshot.
      case (_, x) =>
        // TODO: this could be improved to assert the specific Resolver depending on release-vs-snapshot nature of the version.
        // e.g. sonatype-staging vs sonatype-snapshots
        if (!x.get.name.toLowerCase.contains("sonatype")) {
          throw new PublishValidationFailed("Sonatype", x.get)
        }
    }
  }

  val validatePublishSettingsSetting = validatePublishSettings := validatePublishSettingsTask.value


  private class PublishValidationFailed(message:String) extends RuntimeException with FeedbackProvidedException {
    def this(expectedResolver: String, actual:Resolver) = this(s"""Invalid resolver. Expected: "$expectedResolver" but was "$actual".""")
    override def toString = message
  }

}
