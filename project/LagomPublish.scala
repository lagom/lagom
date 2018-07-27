import sbt.Keys.{ name, publishTo }
import sbt.{ Def, FeedbackProvidedException, Resolver, Task, taskKey }

object LagomPublish {

  val validatePublishSettings = taskKey[Unit]("Validate Lagom settings to publish released artifacts.")

  val validatePublishSettingsTask: Def.Initialize[Task[Unit]] = Def.task {
    val resolverValue: Option[Resolver] = publishTo.value

    // the following implements the rules described in https://github.com/lagom/lagom/issues/1496#issuecomment-408398508
    // TODO: improve rules and validations depending on the version (SNAPSHOT vs release)
    (name.value, resolverValue) match {
      case (_, None) => throw new PublishValidationFailed("`publishTo` not set.")
      case ("lagom-sbt-plugin", x) =>
        if (x.get.name != "Bintray-Sbt-Publish-lagom-sbt-plugin-releases-lagom-sbt-plugin") {
          throw new PublishValidationFailed("""Invalid resolver. Expected: "Raw(Bintray-Sbt-Publish-lagom-sbt-plugin-releases-lagom-sbt-plugin)".""")
        }
      case (_, x) =>
        if (!x.get.name.toLowerCase.contains("sonatype")) {
          throw new PublishValidationFailed(s"""Invalid resolver. Expected: Sonatype. Actual $x.""")
        }
    }
  }

  val validatePublishSettingsSetting = validatePublishSettings := validatePublishSettingsTask.value

  private class PublishValidationFailed(message: String) extends RuntimeException with FeedbackProvidedException {
    override def toString = message
  }

}
