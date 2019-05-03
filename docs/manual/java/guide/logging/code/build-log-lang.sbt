//#lagom-logback-plugin-lagomjava
lazy val usersImpl = (project in file("usersImpl"))
// Lagom logging module is automatically added to the classpath
  .enablePlugins(LagomJava)
//#lagom-logback-plugin-lagomjava

//#lagom-logback-plugin-disabled
lazy val portfolioImpl = (project in file("portfolioImpl"))
  .enablePlugins(LagomJava)
  // This avoids adding the Lagom logging module to the classpath
  .disablePlugins(LagomLogback)
//#lagom-logback-plugin-disabled

//#lagom-log4j2-plugin-lagomjava
lazy val blogImpl = (project in file("blogImpl"))
  .enablePlugins(LagomJava, LagomLog4j2)
  .disablePlugins(LagomLogback)
//#lagom-log4j2-plugin-lagomjava

//#lagom-logback-plugin-disabled-log4j
lazy val orderImpl = (project in file("orderImpl"))
  .enablePlugins(LagomJava)
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
