//#service-gateway-address
lagomServiceGatewayAddress in ThisBuild := "0.0.0.0"
//#service-gateway-address

//#service-gateway-port
lagomServiceGatewayPort in ThisBuild := 9010
//#service-gateway-port

//#service-gateway-impl
// Implementation of the service gateway: "akka-http" (default) or "netty"
lagomServiceGatewayImpl in ThisBuild := "netty"
//#service-gateway-impl

//#service-locator-address
lagomServiceLocatorAddress in ThisBuild := "0.0.0.0"
//#service-locator-address

//#service-locator-port
lagomServiceLocatorPort in ThisBuild := 10000
//#service-locator-port

//#service-locator-unmanaged-services
lagomUnmanagedServices in ThisBuild := Map("weather" -> "http://localhost:3333")
//#service-locator-unmanaged-services

//#service-locator-disabled
lagomServiceLocatorEnabled in ThisBuild := false
//#service-locator-disabled
