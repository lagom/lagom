//#local-instance
lagomCassandraEnabled in ThisBuild := false
lagomUnmanagedServices in ThisBuild := Map("cas_native" -> "http://localhost:9042")
//#local-instance
