//#lagom-immutables
lazy val usersApi = (project in file("usersApi"))
  .settings(libraryDependencies += lagomJavadslImmutables)
//#lagom-immutables