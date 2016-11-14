//#lagom-logback-plugin-disabled-log4j
lazy val portfolioImpl = (project in file("portfolioImpl"))
  .enablePlugins(LagomJava)
  .disablePlugins(LagomLogback) // this avoids that the Lagom logging module is added to the classpath
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.7",
      "org.apache.logging.log4j" % "log4j-api" % "2.7",
      "org.apache.logging.log4j" % "log4j-core" % "2.7"
    )
  )
//#lagom-logback-plugin-disabled-log4j
