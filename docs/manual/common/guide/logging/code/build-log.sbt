//#lagom-logback-libdep
// `LagomImport` provides a few handy alias to several Lagom modules
import com.lightbend.lagom.sbt.LagomImport.lagomLogback

lazy val usersApi = (project in file("usersApi"))
  .settings(libraryDependencies += lagomLogback)
//#lagom-logback-libdep

//#lagom-log4j2-libdep
// `LagomImport` provides a few handy alias to several Lagom modules
import com.lightbend.lagom.sbt.LagomImport.lagomLog4j2

lazy val blogApi = (project in file("blogApi"))
  .settings(libraryDependencies += lagomLog4j2)
//#lagom-log4j2-libdep
