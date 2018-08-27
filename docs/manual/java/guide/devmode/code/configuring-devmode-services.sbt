//#service-locator-address
lagomServiceLocatorAddress in ThisBuild := "0.0.0.0"
//#service-locator-address

//#port-range
lagomServicesPortRange in ThisBuild := PortRange(40000, 45000)
//#port-range

//#service-port
lazy val usersImpl = (project in file("usersImpl"))
  .enablePlugins(LagomJava)
  .settings(lagomServiceHttpPort := 11000)
  .settings(lagomServiceHttpsPort := 11003)
//#service-port

//#service-address
lazy val biddingImpl = (project in file("biddingImpl"))
  .enablePlugins(LagomJava)
  .settings(lagomServiceAddress := "0.0.0.0")
//#service-address
