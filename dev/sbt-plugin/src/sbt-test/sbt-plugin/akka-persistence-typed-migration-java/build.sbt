ThisBuild / organization := "com.example"

// no need for Kafka on this test
ThisBuild / lagomCassandraEnabled := false
ThisBuild / lagomKafkaEnabled := false

val h2Driver = "com.h2database" % "h2" % "1.4.199"
val lombok = "org.projectlombok" % "lombok" % "1.18.18"
val hibernateEntityManager = "org.hibernate" % "hibernate-entitymanager" % "5.4.2.Final"
val jpaApi  = "org.hibernate.javax.persistence" % "hibernate-jpa-2.1-api" % "1.0.0.Final"
val validationApi = "javax.validation" % "validation-api" % "1.1.0.Final"

lazy val `shopping-cart-java` = (project in file("."))
  .aggregate(`shopping-cart-api`, `shopping-cart-lagom-persistence`, `shopping-cart-akka-persistence-typed`)

lazy val `shopping-cart-api` = (project in file("shopping-cart-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomJavadslApi,
      lombok
    )
  )

lazy val `shopping-cart-akka-persistence-typed` = (project in file("shopping-cart-akka-persistence-typed"))
  .enablePlugins(LagomJava)
  .settings(
    libraryDependencies ++= Seq(
      lagomJavadslPersistenceJdbc,
      lagomJavadslPersistenceJpa,
      lombok,
      hibernateEntityManager,
      jpaApi,
      validationApi,
      h2Driver
    )
  )
  .settings(lagomForkedTestSettings)
  .dependsOn(`shopping-cart-api`)

lazy val `shopping-cart-lagom-persistence` = (project in file("shopping-cart-lagom-persistence"))
  .enablePlugins(LagomJava)
  .settings(
    libraryDependencies ++= Seq(
      lagomJavadslPersistenceJdbc,
      lagomJavadslPersistenceJpa,
      lombok,
      hibernateEntityManager,
      jpaApi,
      validationApi,
      h2Driver
    )
  )
  .settings(lagomForkedTestSettings)
  .dependsOn(`shopping-cart-api`)
