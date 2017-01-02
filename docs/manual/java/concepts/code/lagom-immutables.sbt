//#lagom-immutables
lazy val usersApi = (project in file("usersApi"))
  .settings(libraryDependencies += lagomJavadslImmutables)
//#lagom-immutables

//#lagom-immutables-lombok
val lombok = "org.projectlombok" % "lombok" % "1.16.12"
lazy val usersApi = (project in file("usersApi"))
  .settings(libraryDependencies += lombok)
//#lagom-immutables-lombok
