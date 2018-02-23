//#port-range
lagomServicesPortRange in ThisBuild := PortRange(40000, 45000)
//#port-range

//#service-port
lazy val usersImpl = (project in file("usersImpl"))
  .enablePlugins(LagomJava)
  .settings(lagomServicePort := 11000)
//#service-port
