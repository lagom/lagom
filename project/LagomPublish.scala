import sbt.Keys.name
import sbt.Keys.publishTo
import sbt.Keys.version
import sbt.Def
import sbt.FeedbackProvidedException
import sbt.Resolver
import sbt.Task
import sbt.taskKey

object LagomPublish {
  val validatePublishSettings = taskKey[Unit]("Validate Lagom settings to publish released artifacts.")

  val validatePublishSettingsTask: Def.Initialize[Task[Unit]] = Def.task {
    val resolverValue: Option[Resolver] = publishTo.value
    val inReleaseVersion: Boolean       = !version.value.contains("SNAPSHOT")

    // the following implements the rules described in https://github.com/lagom/lagom/issues/1496#issuecomment-408398508
    // TODO: improve rules and validations depending on the version (SNAPSHOT vs release)
    (name.value, resolverValue) match {
      case (_, None)               => throw new PublishValidationFailed("`publishTo` not set.")
      case (_, x) =>
        // TODO: this could be improved to assert the specific Resolver depending on release-vs-snapshot nature of the version.
        // e.g. sonatype-staging vs sonatype-snapshots
        if (!x.get.name.toLowerCase.contains("sonatype")) {
          throw new PublishValidationFailed("Sonatype", x.get)
        }
    }
  }

  val validatePublishSettingsSetting = validatePublishSettings := validatePublishSettingsTask.value

  private class PublishValidationFailed(message: String) extends RuntimeException with FeedbackProvidedException {
    def this(expectedResolver: String, actual: Resolver) =
      this(s"""Invalid resolver. Expected: "$expectedResolver" but was "$actual".""")
    override def toString = message
  }
}
