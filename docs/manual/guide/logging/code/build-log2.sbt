//#lagom-logback-plugin-disabled-log4j
lazy val portfolioImpl = (project in file("portfolioImpl"))
  .enablePlugins(LagomJava)
  .disablePlugins(LagomLogback) // this avoids that the Lagom logging module is addedd to the classpath
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.4.1",
      "org.apache.logging.log4j" % "log4j-api" % "2.4.1",
      "org.apache.logging.log4j" % "log4j-core" % "2.4.1"
    )
  )
//#lagom-logback-plugin-disabled-log4j
