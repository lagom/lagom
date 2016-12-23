//#service-locator-port
lagomServiceLocatorPort in ThisBuild := 10000
//#service-locator-port

//#service-locator-unmanaged-services
lagomUnmanagedServices in ThisBuild := Map("weather" -> "http://localhost:3333")
//#service-locator-unmanaged-services

//#service-locator-disabled
lagomServiceLocatorEnabled in ThisBuild := false
//#service-locator-disabled
