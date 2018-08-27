//#port-range
lagomServicesPortRange in ThisBuild := PortRange(40000, 45000)
//#port-range

//#service-port
lazy val usersImpl = (project in file("usersImpl"))
  .enablePlugins(LagomScala)
  .settings(lagomServiceHttpPort := 11000)
//#service-port

//#service-address
lazy val biddingImpl = (project in file("biddingImpl"))
  .enablePlugins(LagomScala)
  .settings(lagomServiceAddress := "0.0.0.0")
//#service-address
