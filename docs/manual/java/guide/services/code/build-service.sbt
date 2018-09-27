lazy val testkitProj = (project in file("")).enablePlugins(LagomJava)
//#fork
  .settings(lagomForkedTestSettings: _*)
//#fork
  .settings(
//#testkit-dependency
  libraryDependencies += lagomJavadslTestKit
//#testkit-dependency
    ,
//#service-https-port
  lagomServiceHttpsPort := 20443
//#service-https-port
    ,
//#service-enable-ssl
  lagomServiceEnableSsl := true
//#service-enable-ssl
  )
