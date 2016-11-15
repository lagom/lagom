//#lagom-logback-libdep
// `LagomImport` provides a few handy alias to several Lagom modules
import com.lightbend.lagom.sbt.LagomImport.lagomLogback

lazy val usersApi = (project in file("usersApi"))
  .settings(libraryDependencies += lagomLogback)
//#lagom-logback-libdep

//#lagom-logback-plugin-lagomjava
lazy val usersImpl = (project in file("usersImpl"))
  .enablePlugins(LagomJava) // Lagom logging module is automatically added to the classpath
//#lagom-logback-plugin-lagomjava

//#lagom-logback-plugin-disabled
lazy val portfolioImpl = (project in file("portfolioImpl"))
  .enablePlugins(LagomJava)
  .disablePlugins(LagomLogback) // this avoids that the Lagom logging module is addedd to the classpath
//#lagom-logback-plugin-disabled

//#lagom-log4j2-libdep
// `LagomImport` provides a few handy alias to several Lagom modules
import com.lightbend.lagom.sbt.LagomImport.lagomLog4j2

lazy val blogApi = (project in file("blogApi"))
  .settings(libraryDependencies += lagomLog4j2)
//#lagom-log4j2-libdep

//#lagom-log4j2-plugin-lagomjava
lazy val blogImpl = (project in file("blogImpl"))
  .enablePlugins(LagomJava, LagomLog4j2)
  .disablePlugins(LagomLogback)
//#lagom-log4j2-plugin-lagomjava
