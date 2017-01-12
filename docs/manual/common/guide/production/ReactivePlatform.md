# Lightbend Reactive Platform

We recommend that you use [Lightbend Reactive Platform](https://www.lightbend.com/products/lightbend-reactive-platform) to take advantage of the [Split Brain Resolver](http://doc.akka.io/docs/akka/akka-commercial-addons-1.0/java/split-brain-resolver.html) and the [Diagnostics Recorder](http://doc.akka.io/docs/akka/akka-commercial-addons-1.0/common/diagnostics-recorder.html).

Read about the importance of the Split Brain Resolver in the [[Cluster Downing|Cluster#Downing]] documentation.

To use Reactive Platform you need to adjust the build in your project.

1) Create a new file `project/typesafe.properties` (put this in `.gitignore` if the code will be released outside your organization) with the following content:

    typesafe.subscription=YOUR_SUBSCRIPTION_ID

([Click to obtain a subscription ID](https://www.lightbend.com/account/id))

2) Create a new file `project/project/typesafe.sbt` with the following content. (Note: It is `project/project` with two `project`.) The values of `rpVersion` and `rpUrl` are subject to change:

    // Update this when a new patch of Reactive Platform is available
    val rpVersion = "16s01p03"

    // Update this when a major version of Reactive Platform is available
    val rpUrl = "https://repo.typesafe.com/typesafe/for-subscribers-only/7B885384C2F0904E32AA8CEBDB634710AF3DC819"

    addSbtPlugin("com.typesafe.rp" % "sbt-typesafe-rp" % rpVersion)

    // The resolver name must start with typesafe-rp
    resolvers += "typesafe-rp-mvn" at rpUrl

    // The resolver name must start with typesafe-rp
    resolvers += Resolver.url("typesafe-rp-ivy", url(rpUrl))(Resolver.ivyStylePatterns)

3) That is normally everything that is needed, but the current version of Reactive Platform does not include Play 2.5.0 yet, so we must adjust the build to not use the Play version that is included in Reactive Platform. In each project of the `build.sbt` the following setting must be added. That can be done in the `def project` helper method if you use that from the getting started build template.

    // Play 2.5.0 is not part of RP yet
    .settings(rpOverrides := rpOverrides.value.filterNot(_.organization == "com.typesafe.play"))

Similar must also be added to `project/plugins.sbt`:

    // Play 2.5.0 is not part of RP yet
    rpOverrides := rpOverrides.value.filterNot(_.organization == "com.typesafe.play")
