//#service-gateway-address
ThisBuild / lagomServiceGatewayAddress := "0.0.0.0"
//#service-gateway-address

//#service-gateway-port
ThisBuild / lagomServiceGatewayPort := 9010
//#service-gateway-port

//#service-gateway-impl
// Implementation of the service gateway: "akka-http" (default) or "netty"
ThisBuild / lagomServiceGatewayImpl := "netty"
//#service-gateway-impl

//#service-locator-address
ThisBuild / lagomServiceLocatorAddress := "0.0.0.0"
//#service-locator-address

//#service-locator-port
ThisBuild / lagomServiceLocatorPort := 10000
//#service-locator-port

//#service-locator-unmanaged-services
ThisBuild / lagomUnmanagedServices := Map("weather" -> "http://localhost:3333")
//#service-locator-unmanaged-services

//#service-locator-disabled
ThisBuild / lagomServiceLocatorEnabled := false
//#service-locator-disabled
