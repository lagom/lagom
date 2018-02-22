//#port-range
lagomServicesPortRange in ThisBuild := PortRange(40000, 45000)
//#port-range

//#service-port-java
lazy val usersImpl = (project in file("usersImpl"))
  .enablePlugins(LagomJava)
  .settings(lagomServicePort := 11000)
//#service-port-java


//#service-port-scala
lazy val usersImpl = (project in file("usersImpl"))
  .enablePlugins(LagomScala)
  .settings(lagomServicePort := 11000)
//#service-port-scala
