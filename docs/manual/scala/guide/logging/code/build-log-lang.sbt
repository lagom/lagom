//#lagom-logback-plugin-lagomscala
lazy val usersImpl = (project in file("usersImpl"))
// Lagom logging module is automatically added to the classpath
  .enablePlugins(LagomScala)
//#lagom-logback-plugin-lagomscala

//#lagom-logback-plugin-disabled
lazy val portfolioImpl = (project in file("portfolioImpl"))
  .enablePlugins(LagomScala)
  // This avoids adding the Lagom logging module to the classpath
  .disablePlugins(LagomLogback)
//#lagom-logback-plugin-disabled

//#lagom-log4j2-plugin-lagomscala
lazy val blogImpl = (project in file("blogImpl"))
  .enablePlugins(LagomScala, LagomLog4j2)
  .disablePlugins(LagomLogback)
//#lagom-log4j2-plugin-lagomscala

//#lagom-logback-plugin-disabled-log4j
lazy val orderImpl = (project in file("orderImpl"))
  .enablePlugins(LagomScala)
  // This avoids adding the Lagom logging module to the classpath
  .disablePlugins(LagomLogback)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.8.2",
      "org.apache.logging.log4j" % "log4j-api"        % "2.8.2",
      "org.apache.logging.log4j" % "log4j-core"       % "2.8.2",
      "com.lmax"                 % "disruptor"        % "3.3.6"
    )
  )
//#lagom-logback-plugin-disabled-log4j
