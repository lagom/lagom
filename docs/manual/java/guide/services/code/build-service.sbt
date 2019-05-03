lazy val testkitProj = (project in file(""))
  .enablePlugins(LagomJava)
//#fork
  .settings(lagomForkedTestSettings: _*)
//#fork
  .settings(
//#testkit-dependency
    libraryDependencies += lagomJavadslTestKit
//#testkit-dependency
  )
