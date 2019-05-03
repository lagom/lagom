lazy val sslProj = (project in file(""))
  .enablePlugins(LagomJava)
  .settings(
//#service-https-port
    lagomServiceHttpsPort := 20443
//#service-https-port
    ,
//#service-enable-ssl
    lagomServiceEnableSsl := true
//#service-enable-ssl
  )
