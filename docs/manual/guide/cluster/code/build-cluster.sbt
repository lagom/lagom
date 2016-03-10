lazy val clusterProj = (project in file("")).enablePlugins(LagomJava)
  .settings(
//#cluster-dependency
  libraryDependencies += lagomJavadslCluster
//#cluster-dependency
  )

lazy val persistenceProj = (project in file("")).enablePlugins(LagomJava)
  .settings(
//#persistence-dependency
  libraryDependencies += lagomJavadslPersistence
//#persistence-dependency
  )

lazy val pubSubProj = (project in file("")).enablePlugins(LagomJava)
  .settings(
//#pubsub-dependency
  libraryDependencies += lagomJavadslPubSub
//#pubsub-dependency
  )
  
lazy val testkitProj = (project in file("")).enablePlugins(LagomJava)
  .settings(
//#testkit-dependency
  libraryDependencies += lagomJavadslTestKit
//#testkit-dependency
  )
