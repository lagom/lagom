lazy val sslProj = (project in file(""))
  .enablePlugins(LagomJava)
  .settings(
//#service-https-port
    lagomServiceHttpsPort := 20443
//#service-https-port
    ,
//#service-enable-ssl
    ThisBuild / lagomServiceEnableSsl := true
//#service-enable-ssl
  )
