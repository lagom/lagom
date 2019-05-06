lazy val root = (project in file(".")).enablePlugins(LagomJava)

lagomUnmanagedServices in ThisBuild := Map("externalservice" -> "http://localhost:6000")

libraryDependencies += lagomJavadslPersistenceCassandra
