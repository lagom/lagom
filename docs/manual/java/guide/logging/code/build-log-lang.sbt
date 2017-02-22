//#lagom-logback-plugin-lagomxxx
lazy val usersImpl = (project in file("usersImpl"))
  .enablePlugins(LagomJava) // Lagom logging module is automatically added to the classpath
//#lagom-logback-plugin-lagomxxx

//#lagom-logback-plugin-disabled
lazy val portfolioImpl = (project in file("portfolioImpl"))
  .enablePlugins(LagomJava)
  .disablePlugins(LagomLogback) // this avoids that the Lagom logging module is addedd to the classpath
//#lagom-logback-plugin-disabled

//#lagom-log4j2-plugin-lagomjava
lazy val blogImpl = (project in file("blogImpl"))
  .enablePlugins(LagomJava, LagomLog4j2)
  .disablePlugins(LagomLogback)
//#lagom-log4j2-plugin-lagomjava
